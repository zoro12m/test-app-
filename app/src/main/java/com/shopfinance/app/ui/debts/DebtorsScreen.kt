package com.shopfinance.app.ui.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.debts.DebtRecord
import com.shopfinance.app.data.debts.DebtRejection
import com.shopfinance.app.data.debts.DebtorRecord
import com.shopfinance.app.data.debts.DebtsSnapshot
import com.shopfinance.app.ui.common.ErrorBanner
import com.shopfinance.app.ui.common.displayMoney
import com.shopfinance.app.ui.common.displayTotals
import com.shopfinance.app.ui.theme.ShopFinanceTheme
import com.shopfinance.domain.debts.Debt
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
import java.time.Instant
import java.time.LocalDate

/**
 * Everyone who owes the shop money: the total still owed per currency, a name search, an
 * Open / Paid / All filter and one row per person, the oldest unpaid debt first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtorsScreen(
    state: DebtsUiState,
    filter: DebtorFilter,
    query: String,
    rejection: DebtRejection?,
    onFilterChange: (DebtorFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onDismissRejection: () -> Unit,
    onOpenDebtor: (String) -> Unit,
    onAddDebtor: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debts_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (state is DebtsUiState.Ready) {
                Button(
                    onClick = onAddDebtor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.debts_add_debtor))
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (state) {
            DebtsUiState.Loading -> DebtsLoading(modifier = contentModifier)
            is DebtsUiState.Error -> DebtsError(
                accessDenied = state.accessDenied,
                onRetry = onRetry,
                modifier = contentModifier,
            )
            is DebtsUiState.Ready -> {
                val overview = state.overview
                val rows = overview.rows(filter, query)
                LazyColumn(
                    modifier = contentModifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (rejection != null) {
                        item(key = "rejection") {
                            ErrorBanner(message = debtRejectionMessage(rejection), onDismiss = onDismissRejection)
                        }
                    }
                    if (overview.pendingCount > 0) {
                        item(key = "pending") { DebtsPendingNotice(count = overview.pendingCount) }
                    }
                    item(key = "total") { TotalCard(total = overview.totalRemaining) }
                    item(key = "search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            label = { Text(stringResource(R.string.debts_search_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "filter") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (option in DebtorFilter.entries) {
                                FilterChip(
                                    selected = option == filter,
                                    onClick = { onFilterChange(option) },
                                    label = { Text(filterLabel(option)) },
                                )
                            }
                        }
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = emptyListText(filter, searching = query.isNotBlank()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(rows, key = { it.debtor.id }) { row ->
                            Column {
                                DebtorListRow(row = row, onClick = { onOpenDebtor(row.debtor.id) })
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(total: MoneyTotals, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.debts_total_remaining),
                style = MaterialTheme.typography.labelLarge,
            )
            if (total.isZero) {
                Text(
                    text = stringResource(R.string.debts_total_none),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                // One line per currency: dollars and lira are never added together.
                for (amount in total.toList()) {
                    Text(
                        text = displayMoney(amount),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
    }
}

/** A person: name and what they owe on the first line, their oldest unpaid debt under it. */
@Composable
private fun DebtorListRow(row: DebtorRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = row.debtor.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!row.balance.remaining.isZero) {
                Text(
                    text = displayTotals(row.balance.remaining),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                )
            }
        }
        val oldest = row.balance.oldestUnpaid
        val days = row.daysSinceOldestUnpaid
        Text(
            text = when {
                oldest != null && days != null ->
                    stringResource(R.string.debts_oldest_unpaid, dayText(oldest), daysAgoText(days))
                row.balance.isSettled -> stringResource(R.string.debts_settled)
                else -> stringResource(R.string.debts_no_debts)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.pendingSync) {
            Text(
                text = stringResource(R.string.debt_pending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun emptyListText(filter: DebtorFilter, searching: Boolean): String = when {
    searching -> stringResource(R.string.debts_no_match)
    filter == DebtorFilter.OPEN -> stringResource(R.string.debts_empty_open)
    filter == DebtorFilter.PAID -> stringResource(R.string.debts_empty_paid)
    else -> stringResource(R.string.debts_empty_all)
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun DebtorsPreview() {
    val createdAt = Instant.parse("2026-10-22T12:00:00Z")
    fun debtor(id: String, name: String) = DebtorRecord(id, name, null, "", createdAt, pendingSync = false)
    fun debt(id: String, debtorId: String, amount: Money, day: String) =
        DebtRecord(Debt(id, debtorId, amount, LocalDate.parse(day)), createdAt, pendingSync = false)
    val snapshot = DebtsSnapshot(
        debtors = listOf(debtor("r", "رامي"), debtor("h", "هبة"), debtor("k", "كريم")),
        debts = listOf(
            debt("d1", "r", Money(7_000, Currency.USD), "2026-10-01"),
            debt("d2", "h", Money(2_500, Currency.USD), "2026-10-09"),
            debt("d3", "h", Money(500_000, Currency.LBP), "2026-10-12"),
            debt("d4", "k", Money(1_200_000, Currency.LBP), "2026-10-18"),
        ),
        payments = emptyList(),
        fromCache = false,
    )
    ShopFinanceTheme {
        DebtorsScreen(
            state = DebtsUiState.Ready(DebtsOverview(snapshot, LocalDate.of(2026, 10, 22))),
            filter = DebtorFilter.OPEN,
            query = "",
            rejection = null,
            onFilterChange = {},
            onQueryChange = {},
            onDismissRejection = {},
            onOpenDebtor = {},
            onAddDebtor = {},
            onRetry = {},
            onBack = {},
        )
    }
}
