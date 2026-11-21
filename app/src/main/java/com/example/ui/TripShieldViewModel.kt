package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ClaimRecord
import com.example.data.IncidentType
import com.example.data.LedgerEntry
import com.example.data.RiderProfile
import com.example.data.TripAudioPlayer
import com.example.data.TripRecord
import com.example.data.TripShieldAiService
import com.example.data.TripShieldRepository
import com.example.data.TripStatus
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val VM_TAG = "TripShieldVM"

sealed interface AdvisorUiState {
    data object Idle : AdvisorUiState
    data object Thinking : AdvisorUiState
    data class Streaming(val text: String) : AdvisorUiState
    data class Success(val text: String) : AdvisorUiState
    data class Error(val message: String) : AdvisorUiState
}

sealed interface TtsUiState {
    data object Idle : TtsUiState
    data class Synthesizing(val label: String) : TtsUiState
    data class Playing(val label: String) : TtsUiState
    data class Error(val message: String) : TtsUiState
}

data class GpsCoordinate(
    val lat: Double,
    val lng: Double
)

class TripShieldViewModel(
    private val repository: TripShieldRepository,
    private val aiService: TripShieldAiService,
    private val audioPlayer: TripAudioPlayer,
    private val currentUserId: String,
    initialDisplayName: String?,
    initialEmail: String?
) : ViewModel() {

    val profileState: StateFlow<UiState<RiderProfile?>> =
        repository.observeRiderProfile(currentUserId)
            .map<RiderProfile?, UiState<RiderProfile?>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError(error, "Failed to load rider profile")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tripsState: StateFlow<UiState<List<TripRecord>>> =
        repository.observeTrips(currentUserId)
            .map<List<TripRecord>, UiState<List<TripRecord>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError(error, "Failed to load trips")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val claimsState: StateFlow<UiState<List<ClaimRecord>>> =
        repository.observeClaims(currentUserId)
            .map<List<ClaimRecord>, UiState<List<ClaimRecord>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError(error, "Failed to load claims")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ledgerState: StateFlow<UiState<List<LedgerEntry>>> =
        repository.observeLedger(currentUserId)
            .map<List<LedgerEntry>, UiState<List<LedgerEntry>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError(error, "Failed to load ledger")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _advisorState = MutableStateFlow<AdvisorUiState>(AdvisorUiState.Idle)
    val advisorState: StateFlow<AdvisorUiState> = _advisorState.asStateFlow()

    private val _ttsState = MutableStateFlow<TtsUiState>(TtsUiState.Idle)
    val ttsState: StateFlow<TtsUiState> = _ttsState.asStateFlow()

    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Live GPS telemetry + incident pin shared between Live Map and Claim Filing
    private val _currentLocation = MutableStateFlow(GpsCoordinate(12.9716, 77.5946))
    val currentLocation: StateFlow<GpsCoordinate> = _currentLocation.asStateFlow()

    private val _routeBreadcrumbs = MutableStateFlow<List<GpsCoordinate>>(emptyList())
    val routeBreadcrumbs: StateFlow<List<GpsCoordinate>> = _routeBreadcrumbs.asStateFlow()

    private val _incidentPin = MutableStateFlow<GpsCoordinate?>(null)
    val incidentPin: StateFlow<GpsCoordinate?> = _incidentPin.asStateFlow()

    private val _openClaimSheetFromPin = MutableStateFlow(false)
    val openClaimSheetFromPin: StateFlow<Boolean> = _openClaimSheetFromPin.asStateFlow()

    private var advisorJob: Job? = null
    private var ttsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureRiderProfile(currentUserId, initialDisplayName, initialEmail)
                .onFailure { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Could not initialize rider profile."
                }
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun updateLiveGps(lat: Double, lng: Double) {
        val point = GpsCoordinate(lat, lng)
        _currentLocation.value = point
        val activeTrip = (tripsState.value as? UiState.Success)?.data
            ?.firstOrNull { it.tripStatus == TripStatus.ACTIVE }
        if (activeTrip != null) {
            val current = _routeBreadcrumbs.value
            val last = current.lastOrNull()
            if (last == null ||
                kotlin.math.abs(last.lat - lat) > 0.0001 ||
                kotlin.math.abs(last.lng - lng) > 0.0001
            ) {
                _routeBreadcrumbs.value = (current + point).takeLast(120)
            }
        }
    }

    fun setIncidentPin(lat: Double, lng: Double) {
        _incidentPin.value = GpsCoordinate(lat, lng)
        _snackbarMessage.value = "Incident pin placed at ${"%.4f".format(lat)}, ${"%.4f".format(lng)}"
    }

    fun clearIncidentPin() {
        _incidentPin.value = null
    }

    fun requestOpenClaimWithPin() {
        _openClaimSheetFromPin.value = true
    }

    fun consumeOpenClaimWithPin() {
        _openClaimSheetFromPin.value = false
    }

    fun activateTripCover(platformName: String, routeSummary: String) {
        if (_actionInProgress.value) return
        val profile = (profileState.value as? UiState.Success)?.data
        val balance = profile?.walletBalanceInr ?: 0.0
        val loc = _currentLocation.value

        viewModelScope.launch {
            _actionInProgress.value = true
            val result = repository.activateTripCover(
                userId = currentUserId,
                platformName = platformName,
                routeSummary = routeSummary,
                startLat = loc.lat,
                startLng = loc.lng,
                currentWalletBalance = balance
            )
            _actionInProgress.value = false
            result.onSuccess { tripCode ->
                _routeBreadcrumbs.value = listOf(loc)
                _snackbarMessage.value = "$tripCode is now protected up to ₹1,00,000."
                if (profile?.voiceAlertsEnabled == true) {
                    speakAlert(
                        label = "Trip $tripCode Cover Active",
                        message = "TripShield protection active for $tripCode on $platformName. " +
                            "You are covered up to one lakh rupees for medical and road emergencies. Ride safe."
                    )
                }
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Failed to activate trip cover."
            }
        }
    }

    fun endActiveTrip(activeTrip: TripRecord) {
        if (_actionInProgress.value) return
        val loc = _currentLocation.value
        val profile = (profileState.value as? UiState.Success)?.data

        viewModelScope.launch {
            _actionInProgress.value = true
            val result = repository.endActiveTrip(
                userId = currentUserId,
                tripId = activeTrip.id,
                endLat = loc.lat,
                endLng = loc.lng
            )
            _actionInProgress.value = false
            result.onSuccess {
                _routeBreadcrumbs.value = emptyList()
                _snackbarMessage.value = "${activeTrip.tripCode} completed. 10-minute post-drop grace window active."
                if (profile?.voiceAlertsEnabled == true) {
                    speakAlert(
                        label = "Trip ${activeTrip.tripCode} Ended",
                        message = "${activeTrip.tripCode} is now complete. Your ten minute post-drop grace cover remains active."
                    )
                }
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Failed to end active trip."
            }
        }
    }

    fun speakCurrentCoverageStatus() {
        val activeTrip = (tripsState.value as? UiState.Success)?.data
            ?.firstOrNull { it.tripStatus == TripStatus.ACTIVE }
        val balance = (profileState.value as? UiState.Success)?.data?.walletBalanceInr ?: 0.0
        val message = if (activeTrip != null) {
            "You are protected right now under ${activeTrip.tripCode} for ${activeTrip.platformName}. " +
                "Your emergency cover limit is one lakh rupees. Remaining wallet balance is ${balance.toInt()} rupees."
        } else {
            "You are not currently covered on an active trip. Tap Activate Trip Cover for eight rupees before starting your next drop. " +
                "Your wallet balance is ${balance.toInt()} rupees."
        }
        speakAlert(
            label = if (activeTrip != null) "Active Cover Readout" else "Coverage Status Readout",
            message = message
        )
    }

    fun fileClaim(
        tripId: String,
        tripCode: String,
        incidentType: IncidentType,
        description: String,
        requestedInr: Double,
        evidenceAttached: Boolean,
        outsideWindow: Boolean
    ) {
        if (_actionInProgress.value) return
        val pin = _incidentPin.value ?: _currentLocation.value
        val advisorNote = when (val st = _advisorState.value) {
            is AdvisorUiState.Success -> st.text.take(1100)
            else -> ""
        }

        viewModelScope.launch {
            _actionInProgress.value = true
            val result = repository.fileClaim(
                userId = currentUserId,
                tripId = tripId,
                tripCode = tripCode,
                incidentType = incidentType,
                description = description,
                requestedInr = requestedInr,
                incidentLat = pin.lat,
                incidentLng = pin.lng,
                evidenceAttached = evidenceAttached,
                outsideWindow = outsideWindow,
                aiSummary = advisorNote
            )
            _actionInProgress.value = false
            result.onSuccess { claimCode ->
                _incidentPin.value = null
                _snackbarMessage.value = "Claim $claimCode submitted for review."
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Failed to submit claim."
            }
        }
    }

    fun withdrawPendingClaim(claim: ClaimRecord) {
        if (_actionInProgress.value) return
        viewModelScope.launch {
            _actionInProgress.value = true
            val result = repository.withdrawPendingClaim(currentUserId, claim.id)
            _actionInProgress.value = false
            result.onSuccess {
                _snackbarMessage.value = "Claim ${claim.claimCode} withdrawn."
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Could not withdraw claim."
            }
        }
    }

    fun topUpWallet(amountInr: Double = 200.0) {
        if (_actionInProgress.value) return
        val balance = (profileState.value as? UiState.Success)?.data?.walletBalanceInr ?: 0.0
        viewModelScope.launch {
            _actionInProgress.value = true
            val result = repository.topUpWallet(currentUserId, balance, amountInr)
            _actionInProgress.value = false
            result.onSuccess { newBal ->
                _snackbarMessage.value = "Added ₹${"%.2f".format(amountInr)}. New balance: ₹${"%.2f".format(newBal)}"
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Wallet top-up failed."
            }
        }
    }

    fun saveRiderSettings(
        displayName: String,
        vehicleNumber: String,
        partnerFleet: String,
        voiceAlertsEnabled: Boolean
    ) {
        if (_actionInProgress.value) return
        viewModelScope.launch {
            _actionInProgress.value = true
            val result = repository.updateRiderSettings(
                userId = currentUserId,
                displayName = displayName,
                vehicleNumber = vehicleNumber,
                partnerFleet = partnerFleet,
                voiceAlertsEnabled = voiceAlertsEnabled
            )
            _actionInProgress.value = false
            result.onSuccess {
                _snackbarMessage.value = "Rider profile updated."
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Failed to update rider profile."
            }
        }
    }

    fun askClaimAdvisor(question: String, attachToClaimId: String? = null) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.length < 5) {
            _advisorState.value = AdvisorUiState.Error("Please enter at least 5 characters describing your question or incident.")
            return
        }
        advisorJob?.cancel()
        advisorJob = viewModelScope.launch {
            _advisorState.value = AdvisorUiState.Thinking
            val trips = (tripsState.value as? UiState.Success)?.data.orEmpty()
            val activeTrip = trips.firstOrNull { it.tripStatus == TripStatus.ACTIVE }
            var finalText = ""
            try {
                aiService.analyzeClaimStream(cleanQuestion, activeTrip, trips.size).collect { partial ->
                    finalText = partial
                    _advisorState.value = AdvisorUiState.Streaming(partial)
                }
                _advisorState.value = AdvisorUiState.Success(finalText)
                if (!attachToClaimId.isNullOrBlank() && finalText.isNotBlank()) {
                    repository.attachAiSummaryToClaim(currentUserId, attachToClaimId, finalText)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(VM_TAG, "Claim Advisor timed out", e)
                _advisorState.value = AdvisorUiState.Error("Analysis timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Claim Advisor failed", e)
                _advisorState.value = AdvisorUiState.Error(
                    e.localizedMessage ?: "Unable to reach AI Claim Advisor right now."
                )
            }
        }
    }

    fun resetAdvisor() {
        advisorJob?.cancel()
        _advisorState.value = AdvisorUiState.Idle
    }

    fun speakAlert(label: String, message: String) {
        ttsJob?.cancel()
        audioPlayer.stop()
        ttsJob = viewModelScope.launch {
            _ttsState.value = TtsUiState.Synthesizing(label)
            try {
                val audio = aiService.synthesizeSpeech(message)
                _ttsState.value = TtsUiState.Playing(label)
                audioPlayer.play(audio) {
                    _ttsState.value = TtsUiState.Idle
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(VM_TAG, "TTS synthesis timed out", e)
                _ttsState.value = TtsUiState.Error("Voice synthesis timed out. Please try again.")
            } catch (e: CancellationException) {
                audioPlayer.stop()
                _ttsState.value = TtsUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Gemini TTS failed", e)
                _ttsState.value = TtsUiState.Error(
                    e.localizedMessage ?: "Voice alert unavailable right now."
                )
            }
        }
    }

    fun stopSpeech() {
        ttsJob?.cancel()
        audioPlayer.stop()
        _ttsState.value = TtsUiState.Idle
    }

    private fun handleStreamError(error: Throwable, fallback: String): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(VM_TAG, fallback, rootError)
        return UiState.Error(rootError.localizedMessage ?: fallback)
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }

    companion object {
        fun create(
            appContext: Context,
            userId: String,
            displayName: String?,
            email: String?
        ): TripShieldViewModel {
            return TripShieldViewModel(
                repository = TripShieldRepository(appContext),
                aiService = TripShieldAiService(),
                audioPlayer = TripAudioPlayer(appContext.applicationContext),
                currentUserId = userId,
                initialDisplayName = displayName,
                initialEmail = email
            )
        }
    }
}
