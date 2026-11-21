package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

private const val AI_TAG = "TripShieldAiService"

data class SynthesizedAudio(
    val bytes: ByteArray,
    val mimeType: String
)

class TripShieldAiService {

    // Deep reasoning Claim & Policy Advisor using gemini-4-carbon with ThinkingLevel.HIGH (no maxOutputTokens)
    private val advisorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-carbon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            },
            systemInstruction = content {
                text(
                    """
                    You are the TripShield Claim & Policy Advisor for gig delivery riders in India.
                    Policy terms you apply:
                    - Per-trip cover costs ₹8.00 and covers up to ₹1,00,000 for emergency medical & accidental injury, ₹15,000 for road breakdown/towing, ₹1,200 for severe monsoon/flood disruption, and ₹25,000 for delivery gear/parcel theft during an ACTIVE trip window plus a 10-minute post-drop grace window.
                    - Incidents occurring outside an active trip or grace window are marked "Outside Trip Window" and may only qualify for goodwill review if GPS shows en-route return to hub.
                    Respond in calm, plain English (no insurance jargon). Structure your reply briefly with:
                    1. Eligibility Verdict (Covered / Needs Review / Outside Window)
                    2. Estimated Payout Range in ₹ INR
                    3. Exact Next Steps & Documents Needed
                    """.trimIndent()
                )
            }
        )
    }

    // Hands-free Text-to-Speech model using gemini-3.8-flash-tts
    private val ttsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash-tts",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
            }
        )
    }

    fun analyzeClaimStream(
        question: String,
        activeTrip: TripRecord?,
        recentTripsCount: Int
    ): Flow<String> = flow {
        val tripContext = if (activeTrip != null) {
            "Active Trip: ${activeTrip.tripCode} (${activeTrip.platformName}), Status: ${activeTrip.status}, " +
                "Route: ${activeTrip.routeSummary}, Coverage Cap: ₹${activeTrip.coverageLimitInr.toInt()}."
        } else {
            "No trip is currently marked ACTIVE. Rider has $recentTripsCount recorded trips."
        }
        val prompt = """
            Rider Context: $tripContext
            Rider Question / Incident Scenario:
            $question
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            advisorModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Advisor returned an empty response.")
        }
    }

    suspend fun synthesizeSpeech(textToSpeak: String): SynthesizedAudio {
        val cleanText = textToSpeak.trim().take(600)
        return withTimeout(45_000L) {
            val response = ttsModel.generateContent(
                content {
                    text("Read the following TripShield rider alert clearly, calmly, and concisely:\n$cleanText")
                }
            )
            val audioPart = response.candidates
                .asSequence()
                .flatMap { it.content.parts.asSequence() }
                .filterIsInstance<InlineDataPart>()
                .firstOrNull { it.mimeType.startsWith("audio/", ignoreCase = true) }
                ?: throw IllegalStateException("TTS model did not return an audio payload.")

            SynthesizedAudio(
                bytes = audioPart.inlineData,
                mimeType = audioPart.mimeType
            )
        }
    }
}

class TripAudioPlayer(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    suspend fun play(audio: SynthesizedAudio, onCompleted: () -> Unit) {
        stop()
        withContext(Dispatchers.IO) {
            try {
                if (audio.mimeType.contains("pcm", ignoreCase = true) ||
                    audio.mimeType.contains("L16", ignoreCase = true)
                ) {
                    val sampleRate = parseSampleRate(audio.mimeType) ?: 24000
                    playPcm16(audio.bytes, sampleRate)
                    withContext(Dispatchers.Main) { onCompleted() }
                } else {
                    playEncodedFile(audio.bytes, audio.mimeType, onCompleted)
                }
            } catch (e: CancellationException) {
                stop()
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Audio playback failed for mimeType=${audio.mimeType}", e)
                stop()
                withContext(Dispatchers.Main) { onCompleted() }
            }
        }
    }

    private fun parseSampleRate(mimeType: String): Int? {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun playPcm16(pcmBytes: ByteArray, sampleRate: Int) {
        if (pcmBytes.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, pcmBytes.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        track.write(pcmBytes, 0, pcmBytes.size)
        track.play()
        val durationMs = ((pcmBytes.size.toLong() / 2L) * 1000L / sampleRate.toLong()).coerceAtLeast(250L)
        Thread.sleep(durationMs + 150L)
        if (audioTrack === track) {
            track.stop()
            track.release()
            audioTrack = null
        }
    }

    private suspend fun playEncodedFile(
        bytes: ByteArray,
        mimeType: String,
        onCompleted: () -> Unit
    ) {
        val ext = when {
            mimeType.contains("wav", ignoreCase = true) -> ".wav"
            mimeType.contains("ogg", ignoreCase = true) -> ".ogg"
            else -> ".mp3"
        }
        val file = File.createTempFile("tripshield_tts_", ext, context.cacheDir)
        file.writeBytes(bytes)
        tempFile = file

        withContext(Dispatchers.Main) {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stop()
                    onCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onCompleted()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = mp
        }
    }

    fun stop() {
        try {
            audioTrack?.let { track ->
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        } finally {
            audioTrack = null
        }
        try {
            mediaPlayer?.let { mp ->
                runCatching { if (mp.isPlaying) mp.stop() }
                runCatching { mp.release() }
            }
        } finally {
            mediaPlayer = null
        }
        runCatching { tempFile?.delete() }
        tempFile = null
    }
}
