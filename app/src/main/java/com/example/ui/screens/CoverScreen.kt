package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RiderProfile
import com.example.data.TripRecord
import com.example.data.TripStatus
import com.example.data.UiState
import com.example.ui.TtsUiState
import com.example.ui.components.BadgeTone
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonospaceValue
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.TripShieldCard
import com.example.ui.components.VoiceReadoutBanner
import com.example.ui.components.formatInr
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.MonoTypography
import kotlinx.coroutines.delay

private val DeliveryPlatforms = listOf("Swiggy", "Zomato", "Blinkit", "Zepto", "Porter")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoverScreen(
    profileState: UiState<RiderProfile?>,
    tripsState: UiState<List<TripRecord>>,
    ttsState: TtsUiState,
    actionInProgress: Boolean,
    onActivateTrip: (platform: String, routeSummary: String) -> Unit,
    onEndTrip: (TripRecord) -> Unit,
    onListenStatus: () -> Unit,
    onStopSpeech: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenLedger: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val trips = (tripsState as? UiState.Success)?.data.orEmpty()
    val activeTrip = trips.firstOrNull { it.tripStatus == TripStatus.ACTIVE }

    var selectedPlatform by remember(profile?.partnerFleet) {
        mutableStateOf(profile?.partnerFleet?.takeIf { it in DeliveryPlatforms } ?: "Swiggy")
    }
    var routeNote by remember { mutableStateOf("Indiranagar -> Koramangala Drop") }

    var elapsedSeconds by remember(activeTrip?.id) { mutableLongStateOf(0L) }
    LaunchedEffect(activeTrip?.id, activeTrip?.createdAt) {
        if (activeTrip != null) {
            while (true) {
                val startMs = activeTrip.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                elapsedSeconds = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val heroBg by animateColorAsState(
        targetValue = if (activeTrip != null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(),
        label = "cover_hero_bg"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cover_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = profile?.displayName?.let { "Hello, $it" } ?: "Rider Protection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    onClick = onOpenLedger,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("cover_wallet_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = "Wallet balance",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        MonospaceValue(
                            text = formatInr(profile?.walletBalanceInr ?: 0.0),
                            style = MonoTypography.Medium
                        )
                    }
                }
            }
        }

        // Active Voice Banner
        if (ttsState !is TtsUiState.Idle) {
            item {
                VoiceReadoutBanner(ttsState = ttsState, onStop = onStopSpeech)
            }
        }

        // Hero Coverage Card ("Am I covered right now?")
        item {
            TripShieldCard(
                containerColor = heroBg,
                borderColor = if (activeTrip != null) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.testTag("cover_hero_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        text = if (activeTrip != null) {
                            stringResource(R.string.cover_protected_now)
                        } else {
                            stringResource(R.string.cover_not_active)
                        },
                        tone = if (activeTrip != null) BadgeTone.PROTECTED else BadgeTone.PENDING,
                        icon = if (activeTrip != null) Icons.Outlined.GppGood else Icons.Outlined.Shield
                    )
                    if (activeTrip != null) {
                        MonospaceValue(
                            text = activeTrip.tripCode,
                            style = MonoTypography.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (activeTrip != null) {
                    Text(
                        text = "Active Cover · ${activeTrip.platformName}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeTrip.routeSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricTile(
                            label = "Cover Cap",
                            value = formatInr(activeTrip.coverageLimitInr),
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Elapsed",
                            value = formatElapsed(elapsedSeconds),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onEndTrip(activeTrip) },
                            enabled = !actionInProgress,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("end_trip_cover_button")
                        ) {
                            if (actionInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.cover_end_cta))
                            }
                        }
                        FilledTonalButton(
                            onClick = onOpenMap,
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("open_live_map_button")
                        ) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = "Open Live Map", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Map")
                        }
                    }
                } else {
                    Text(
                        text = "Ready for your next drop?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "One tap activates ₹1,00,000 emergency medical & road cover plus a 10-minute post-drop grace window.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DeliveryPlatforms.forEach { platform ->
                            FilterChip(
                                selected = selectedPlatform == platform,
                                onClick = { selectedPlatform = platform },
                                label = { Text(platform, style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("platform_chip_${platform.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = routeNote,
                        onValueChange = { routeNote = it.take(120) },
                        label = { Text("Route / Drop Zone") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("route_summary_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onActivateTrip(selectedPlatform, routeNote) },
                        enabled = !actionInProgress,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("activate_trip_cover_button")
                    ) {
                        if (actionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cover_activate_cta),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FilledTonalButton(
                    onClick = onListenStatus,
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("listen_cover_status_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cover_listen_cta))
                }
            }
        }

        // Quick Policy Highlights
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PolicySummaryBox(
                    title = "Medical Cover",
                    amount = "₹1,00,000",
                    subtitle = "Cashless ER",
                    modifier = Modifier.weight(1f)
                )
                PolicySummaryBox(
                    title = "Breakdown",
                    amount = "₹15,000",
                    subtitle = "24/7 Tow & Fix",
                    modifier = Modifier.weight(1f)
                )
                PolicySummaryBox(
                    title = "Grace Window",
                    amount = "10 min",
                    subtitle = "Post-drop",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Recent Trips Header
        item {
            SectionHeader(
                title = stringResource(R.string.cover_recent_trips),
                trailing = {
                    MonospaceValue(
                        text = "${trips.size} trips",
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        when (tripsState) {
            is UiState.Loading -> {
                item {
                    TripShieldCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Syncing your protected trips…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            is UiState.Error -> {
                item {
                    TripShieldCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = tripsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (trips.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.DirectionsBike,
                            title = "No trips protected yet",
                            description = "Tap Activate Trip Cover above before your next pickup to start your real-time protection log."
                        )
                    }
                } else {
                    items(trips, key = { it.id }) { trip ->
                        TripRowItem(trip = trip)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MonospaceValue(
                text = value,
                style = MonoTypography.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PolicySummaryBox(
    title: String,
    amount: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MonospaceValue(
                text = amount,
                style = MonoTypography.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TripRowItem(trip: TripRecord) {
    val tone = when (trip.tripStatus) {
        TripStatus.ACTIVE -> BadgeTone.PROTECTED
        TripStatus.COMPLETED -> BadgeTone.NEUTRAL
        TripStatus.FLAGGED -> BadgeTone.FLAGGED
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trip_row_${trip.tripCode}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${trip.platformName} · ${trip.routeSummary}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MonospaceValue(
                        text = formatTimestampShort(trip.createdAt),
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    MonospaceValue(
                        text = trip.tripCode,
                        style = MonoTypography.Medium
                    )
                    MonospaceValue(
                        text = formatInr(-trip.premiumInr),
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(text = trip.tripStatus.label, tone = tone)
                MonospaceValue(
                    text = "Cap ${formatInr(trip.coverageLimitInr)}",
                    style = MonoTypography.Small,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins, secs) else "%02d:%02d".format(mins, secs)
}
