package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ClaimRecord
import com.example.data.ClaimStatus
import com.example.data.IncidentType
import com.example.data.TripRecord
import com.example.data.UiState
import com.example.ui.AdvisorUiState
import com.example.ui.GpsCoordinate
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

private enum class ClaimFilter(val label: String) {
    ALL("All"),
    PENDING("Under Review"),
    APPROVED("Approved / Paid"),
    OUTSIDE("Outside Window")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClaimsScreen(
    claimsState: UiState<List<ClaimRecord>>,
    tripsState: UiState<List<TripRecord>>,
    advisorState: AdvisorUiState,
    ttsState: TtsUiState,
    incidentPin: GpsCoordinate?,
    openClaimSheetImmediately: Boolean,
    actionInProgress: Boolean,
    onConsumeOpenClaimSheet: () -> Unit,
    onFileClaim: (
        tripId: String,
        tripCode: String,
        incidentType: IncidentType,
        description: String,
        requestedInr: Double,
        evidenceAttached: Boolean,
        outsideWindow: Boolean
    ) -> Unit,
    onWithdrawClaim: (ClaimRecord) -> Unit,
    onAskAdvisor: (String) -> Unit,
    onResetAdvisor: () -> Unit,
    onSpeakText: (label: String, text: String) -> Unit,
    onStopSpeech: () -> Unit
) {
    val claims = (claimsState as? UiState.Success)?.data.orEmpty()
    val trips = (tripsState as? UiState.Success)?.data.orEmpty()

    var selectedFilter by remember { mutableStateOf(ClaimFilter.ALL) }
    var showFileClaimSheet by remember { mutableStateOf(false) }
    var showAdvisorSheet by remember { mutableStateOf(false) }

    LaunchedEffect(openClaimSheetImmediately) {
        if (openClaimSheetImmediately) {
            showFileClaimSheet = true
            onConsumeOpenClaimSheet()
        }
    }

    val filteredClaims = remember(claims, selectedFilter) {
        when (selectedFilter) {
            ClaimFilter.ALL -> claims
            ClaimFilter.PENDING -> claims.filter { it.parsedStatus == ClaimStatus.PENDING }
            ClaimFilter.APPROVED -> claims.filter {
                it.parsedStatus == ClaimStatus.APPROVED || it.parsedStatus == ClaimStatus.PAID
            }
            ClaimFilter.OUTSIDE -> claims.filter { it.parsedStatus == ClaimStatus.OUTSIDE_WINDOW }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("claims_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Claims & Protection",
                trailing = {
                    MonospaceValue(
                        text = "${claims.size} total",
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        if (ttsState !is TtsUiState.Idle) {
            item {
                VoiceReadoutBanner(ttsState = ttsState, onStop = onStopSpeech)
            }
        }

        // Primary Actions: File Claim & AI Claim Advisor
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showFileClaimSheet = true },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_file_claim_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.claims_new_cta))
                }
                FilledTonalButton(
                    onClick = { showAdvisorSheet = true },
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_ai_advisor_button")
                ) {
                    Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.claims_advisor_cta))
                }
            }
        }

        // Highlight card introducing Deep-Thinking AI Advisor
        item {
            TripShieldCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Not sure if an incident is covered?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ask the TripShield AI Claim Advisor to check trip-window overlap, grace periods, payout limits, and exact receipts needed—with optional hands-free voice readout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Filter Chips
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ClaimFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("claim_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        when (claimsState) {
            is UiState.Loading -> {
                item {
                    TripShieldCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Loading your claims…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            is UiState.Error -> {
                item {
                    TripShieldCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = claimsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (filteredClaims.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.Description,
                            title = "No claims in this view",
                            description = "Tap File Claim to report a road accident, breakdown, or weather disruption tied to a protected trip."
                        )
                    }
                } else {
                    items(filteredClaims, key = { it.id }) { claim ->
                        ClaimCardItem(
                            claim = claim,
                            onWithdraw = { onWithdrawClaim(claim) },
                            onSpeakSummary = {
                                val text = if (claim.aiSummary.isNotBlank()) {
                                    "Claim ${claim.claimCode} status is ${claim.parsedStatus.label}. ${claim.aiSummary}"
                                } else {
                                    "Claim ${claim.claimCode} for ${claim.parsedIncidentType.label} on ${claim.tripCode} is ${claim.parsedStatus.label} for ${claim.requestedInr.toInt()} rupees."
                                }
                                onSpeakText("Claim ${claim.claimCode}", text)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFileClaimSheet) {
        FileClaimBottomSheet(
            trips = trips,
            incidentPin = incidentPin,
            actionInProgress = actionInProgress,
            onDismiss = { showFileClaimSheet = false },
            onSubmit = { tripId, tripCode, type, desc, amount, hasPhoto, outsideWindow ->
                onFileClaim(tripId, tripCode, type, desc, amount, hasPhoto, outsideWindow)
                showFileClaimSheet = false
            }
        )
    }

    if (showAdvisorSheet) {
        AiAdvisorBottomSheet(
            advisorState = advisorState,
            ttsState = ttsState,
            onDismiss = { showAdvisorSheet = false },
            onAsk = onAskAdvisor,
            onReset = onResetAdvisor,
            onSpeakSummary = { text -> onSpeakText("AI Claim Advisor", text) },
            onStopSpeech = onStopSpeech
        )
    }
}

@Composable
private fun ClaimCardItem(
    claim: ClaimRecord,
    onWithdraw: () -> Unit,
    onSpeakSummary: () -> Unit
) {
    val badgeTone = when (claim.parsedStatus) {
        ClaimStatus.APPROVED, ClaimStatus.PAID -> BadgeTone.PROTECTED
        ClaimStatus.PENDING -> BadgeTone.PENDING
        ClaimStatus.OUTSIDE_WINDOW -> BadgeTone.FLAGGED
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("claim_card_${claim.claimCode}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(text = claim.parsedStatus.label, tone = badgeTone)
                MonospaceValue(
                    text = claim.claimCode,
                    style = MonoTypography.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = claim.parsedIncidentType.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MonospaceValue(
                        text = "${claim.tripCode} · ${formatTimestampShort(claim.createdAt)}",
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                MonospaceValue(
                    text = formatInr(claim.requestedInr),
                    style = MonoTypography.Large,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = claim.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (claim.incidentLat != null && claim.incidentLng != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    MonospaceValue(
                        text = "Pin ${"%.4f".format(claim.incidentLat)}, ${"%.4f".format(claim.incidentLng)}",
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (claim.aiSummary.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI Advisor: ${claim.aiSummary.take(260)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSpeakSummary,
                    modifier = Modifier.testTag("speak_claim_${claim.claimCode}")
                ) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.claims_read_aloud))
                }
                if (claim.parsedStatus == ClaimStatus.PENDING) {
                    TextButton(
                        onClick = onWithdraw,
                        modifier = Modifier.testTag("withdraw_claim_${claim.claimCode}")
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Withdraw", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FileClaimBottomSheet(
    trips: List<TripRecord>,
    incidentPin: GpsCoordinate?,
    actionInProgress: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        tripId: String,
        tripCode: String,
        incidentType: IncidentType,
        description: String,
        requestedInr: Double,
        evidenceAttached: Boolean,
        outsideWindow: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTrip by remember(trips) { mutableStateOf(trips.firstOrNull()) }
    var incidentType by remember { mutableStateOf(IncidentType.ACCIDENT) }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("1200") }
    var outsideWindow by remember { mutableStateOf(trips.isEmpty()) }
    var evidenceUri by remember { mutableStateOf<Uri?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Zero-permission Android Photo Picker (Google Play policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) evidenceUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "File a TripShield Claim",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (incidentPin != null) {
                StatusBadge(
                    text = "GPS Pin Attached · ${"%.4f".format(incidentPin.lat)}, ${"%.4f".format(incidentPin.lng)}",
                    tone = BadgeTone.PROTECTED,
                    icon = Icons.Outlined.LocationOn
                )
            }

            if (trips.isNotEmpty()) {
                Text("Select Protected Trip", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trips.take(4).forEach { trip ->
                        FilterChip(
                            selected = !outsideWindow && selectedTrip?.id == trip.id,
                            onClick = {
                                selectedTrip = trip
                                outsideWindow = false
                            },
                            label = { MonospaceValue(trip.tripCode, style = MonoTypography.Small) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Happened outside an active trip window",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = outsideWindow,
                    onCheckedChange = { outsideWindow = it },
                    modifier = Modifier.testTag("claim_outside_window_switch")
                )
            }

            Text("Incident Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IncidentType.entries.forEach { type ->
                    FilterChip(
                        selected = incidentType == type,
                        onClick = { incidentType = type },
                        label = { Text(type.label) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("incident_type_${type.name.lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) },
                label = { Text("Estimated Claim Amount (₹ INR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("claim_amount_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(600) },
                label = { Text("What happened? (min 5 characters)") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("claim_description_input")
            )

            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("claim_attach_photo_button")
            ) {
                Icon(
                    imageVector = if (evidenceUri != null) Icons.Outlined.CheckCircle else Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (evidenceUri != null) "Photo Attached · Tap to Change" else stringResource(R.string.claims_attach_photo)
                )
            }

            if (evidenceUri != null) {
                AsyncImage(
                    model = evidenceUri,
                    contentDescription = "Attached claim evidence preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    when {
                        description.trim().length < 5 ->
                            validationError = "Please describe the incident in at least 5 characters."
                        amount == null || amount < 1.0 || amount > 500000.0 ->
                            validationError = "Enter a valid claim amount between ₹1 and ₹5,00,000."
                        else -> {
                            validationError = null
                            val linkedTrip = if (outsideWindow) null else selectedTrip
                            onSubmit(
                                linkedTrip?.id ?: "OUTSIDE_TRIP",
                                linkedTrip?.tripCode ?: "OUTSIDE_TRIP",
                                incidentType,
                                description.trim(),
                                amount,
                                evidenceUri != null,
                                outsideWindow
                            )
                        }
                    }
                },
                enabled = !actionInProgress,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_claim_button")
            ) {
                Text(
                    text = "Submit Claim",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAdvisorBottomSheet(
    advisorState: AdvisorUiState,
    ttsState: TtsUiState,
    onDismiss: () -> Unit,
    onAsk: (String) -> Unit,
    onReset: () -> Unit,
    onSpeakSummary: (String) -> Unit,
    onStopSpeech: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var question by remember {
        mutableStateOf("I slipped in heavy rain 6 minutes after marking a Swiggy order delivered and paid ₹1,450 at a clinic. Am I covered?")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(R.string.claims_advisor_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Deep policy reasoning · High Thinking Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = question,
                onValueChange = { question = it.take(500) },
                label = { Text("Describe your incident or coverage question") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advisor_question_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onAsk(question) },
                    enabled = advisorState !is AdvisorUiState.Thinking && advisorState !is AdvisorUiState.Streaming,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("advisor_analyze_button")
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analyze Eligibility")
                }
                if (advisorState !is AdvisorUiState.Idle) {
                    OutlinedButton(
                        onClick = onReset,
                        shape = CircleShape,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Clear")
                    }
                }
            }

            when (advisorState) {
                is AdvisorUiState.Idle -> Unit
                is AdvisorUiState.Thinking -> {
                    TripShieldCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Reasoning carefully through TripShield policy rules and grace windows…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                is AdvisorUiState.Streaming -> {
                    TripShieldCard {
                        Text(
                            text = advisorState.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is AdvisorUiState.Success -> {
                    TripShieldCard(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.testTag("advisor_response_card")
                    ) {
                        Text(
                            text = advisorState.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val speaking = ttsState is TtsUiState.Playing || ttsState is TtsUiState.Synthesizing
                        FilledTonalButton(
                            onClick = {
                                if (speaking) onStopSpeech() else onSpeakSummary(advisorState.text)
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("advisor_read_aloud_button")
                        ) {
                            Icon(
                                imageVector = if (speaking) Icons.Outlined.StopCircle else Icons.Outlined.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (speaking) stringResource(R.string.claims_stop_audio)
                                else stringResource(R.string.claims_read_aloud)
                            )
                        }
                    }
                }
                is AdvisorUiState.Error -> {
                    TripShieldCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = advisorState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
