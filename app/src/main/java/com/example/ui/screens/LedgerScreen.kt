package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.LedgerEntry
import com.example.data.LedgerType
import com.example.data.RiderProfile
import com.example.data.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonospaceValue
import com.example.ui.components.SectionHeader
import com.example.ui.components.TripShieldCard
import com.example.ui.components.formatInr
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.StatusProtectedText

private enum class LedgerFilter(val label: String) {
    ALL("All"),
    PREMIUM("Premiums"),
    PAYOUT("Payouts"),
    TOPUP("Top-Ups")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerScreen(
    profileState: UiState<RiderProfile?>,
    ledgerState: UiState<List<LedgerEntry>>,
    actionInProgress: Boolean,
    onTopUpWallet: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val entries = (ledgerState as? UiState.Success)?.data.orEmpty()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(LedgerFilter.ALL) }

    val filteredEntries = remember(entries, searchQuery, selectedFilter) {
        entries.filter { entry ->
            val matchesType = when (selectedFilter) {
                LedgerFilter.ALL -> true
                LedgerFilter.PREMIUM -> entry.parsedType == LedgerType.PREMIUM
                LedgerFilter.PAYOUT -> entry.parsedType == LedgerType.PAYOUT
                LedgerFilter.TOPUP -> entry.parsedType == LedgerType.TOPUP
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                entry.title.contains(q, ignoreCase = true) ||
                entry.referenceCode.contains(q, ignoreCase = true)
            matchesType && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ledger_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TripShieldCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.ledger_balance_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                MonospaceValue(
                    text = formatInr(profile?.walletBalanceInr ?: 0.0),
                    style = MonoTypography.Large.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("ledger_wallet_balance")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Covers up to ${((profile?.walletBalanceInr ?: 0.0) / 8.0).toInt()} protected delivery trips at ₹8.00/trip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onTopUpWallet,
                    enabled = !actionInProgress,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("ledger_topup_button")
                ) {
                    if (actionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ledger_topup_cta))
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(60) },
                placeholder = { Text(stringResource(R.string.ledger_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ledger_search_input")
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LedgerFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("ledger_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "INR Ledger",
                trailing = {
                    MonospaceValue(
                        text = "${filteredEntries.size} rows",
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        when (ledgerState) {
            is UiState.Loading -> {
                item {
                    TripShieldCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Syncing INR ledger…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            is UiState.Error -> {
                item {
                    TripShieldCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = ledgerState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (filteredEntries.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "No matching ledger entries",
                            description = "Activate a trip cover or top up your TripShield wallet to see itemized INR transactions."
                        )
                    }
                } else {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                filteredEntries.forEachIndexed { idx, entry ->
                                    LedgerRow(entry = entry)
                                    if (idx < filteredEntries.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry) {
    val isCredit = entry.amountInr >= 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("ledger_row_${entry.referenceCode}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            MonospaceValue(
                text = "${entry.referenceCode} · ${formatTimestampShort(entry.createdAt)}",
                style = MonoTypography.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MonospaceValue(
                text = formatInr(entry.amountInr, showSign = true),
                style = MonoTypography.Medium,
                color = if (isCredit) StatusProtectedText else MaterialTheme.colorScheme.onSurface
            )
            MonospaceValue(
                text = "Bal ${formatInr(entry.balanceAfterInr)}",
                style = MonoTypography.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
