package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.TtsUiState
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.StatusFlaggedBg
import com.example.ui.theme.StatusFlaggedText
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingText
import com.example.ui.theme.StatusProtectedBg
import com.example.ui.theme.StatusProtectedText
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

enum class BadgeTone {
    PROTECTED,
    PENDING,
    FLAGGED,
    NEUTRAL
}

@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val (bg, fg) = when (tone) {
        BadgeTone.PROTECTED -> StatusProtectedBg to StatusProtectedText
        BadgeTone.PENDING -> StatusPendingBg to StatusPendingText
        BadgeTone.FLAGGED -> StatusFlaggedBg to StatusFlaggedText
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = fg
            )
        }
    }
}

@Composable
fun TripShieldCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun VoiceReadoutBanner(
    ttsState: TtsUiState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (ttsState is TtsUiState.Idle) return

    val (bg, fg, title) = when (ttsState) {
        is TtsUiState.Synthesizing -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Preparing voice alert: ${ttsState.label}…"
        )
        is TtsUiState.Playing -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Speaking: ${ttsState.label}"
        )
        is TtsUiState.Error -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            ttsState.message
        )
        is TtsUiState.Idle -> return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_readout_banner"),
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (ttsState) {
                is TtsUiState.Synthesizing -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                is TtsUiState.Playing -> Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = "Voice readout active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                is TtsUiState.Error -> Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = "Voice alert error",
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
                is TtsUiState.Idle -> Unit
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onStop,
                modifier = Modifier.testTag("voice_readout_stop_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.StopCircle,
                    contentDescription = "Dismiss voice readout",
                    tint = fg
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    TripShieldCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatInr(amount: Double, showSign: Boolean = false): String {
    val indiaLocale = Locale("en", "IN")
    val formatter = NumberFormat.getNumberInstance(indiaLocale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val absFormatted = formatter.format(kotlin.math.abs(amount))
    return when {
        amount < 0 -> "-₹$absFormatted"
        showSign && amount > 0 -> "+₹$absFormatted"
        else -> "₹$absFormatted"
    }
}

fun formatTimestampShort(timestamp: Timestamp?): String {
    val date = (timestamp ?: Timestamp.now()).toDate()
    val sdf = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
    return sdf.format(date)
}

@Composable
fun MonospaceValue(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MonoTypography.Medium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier
    )
}
