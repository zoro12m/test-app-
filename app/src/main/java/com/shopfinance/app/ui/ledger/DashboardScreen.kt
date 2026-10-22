package com.shopfinance.app.ui.ledger

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.ledger.LedgerRecord
import com.shopfinance.app.data.ledger.LedgerRejection
import com.shopfinance.app.data.shop.Shop
import com.shopfinance.app.ui.common.ErrorBanner
import com.shopfinance.app.ui.common.displayMoney
import com.shopfinance.app.ui.theme.ShopFinanceTheme
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.ledger.LedgerEntry
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import java.time.Instant

/** How many of the latest entries the dashboard lists before "show all". */
private const val RECENT_ENTRIES = 10

/**
 * The shop's home: cash on hand, today's and this month's sales and expenses, buttons to
 * record money moving, the way into the debts book, and the latest entries. Tapping an entry
 * that is not voided offers to void it ([onVoid]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    shop: Shop,
    state: LedgerUiState,
    rejection: LedgerRejection?,
    onDismissRejection: () -> Unit,
    onNewEntry: (EntryType) -> Unit,
    onShowAll: () -> Unit,
    onOpenDebts: () -> Unit,
    onVoid: (LedgerRecord) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = shop.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(R.string.action_sign_out))
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (state) {
            LedgerUiState.Loading -> LedgerLoading(modifier = contentModifier)
            is LedgerUiState.Error -> LedgerError(
                accessDenied = state.accessDenied,
                onRetry = onRetry,
                modifier = contentModifier,
            )
            is LedgerUiState.Ready -> LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (rejection != null) {
                    item(key = "rejection") {
                        ErrorBanner(message = rejectionMessage(rejection), onDismiss = onDismissRejection)
                    }
                }
                if (state.pendingCount > 0) {
                    item(key = "pending") { PendingNotice(count = state.pendingCount) }
                }
                item(key = "summary") { SummaryCard(summary = state.summary) }
                item(key = "actions") { EntryActions(onNewEntry = onNewEntry) }
                item(key = "debts") {
                    // Debts are a separate book: they never change the cash on hand above.
                    FilledTonalButton(onClick = onOpenDebts, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dashboard_action_debts))
                    }
                }
                item(key = "recent-title") {
                    Text(
                        text = stringResource(R.string.dashboard_recent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.records.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.dashboard_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.records.take(RECENT_ENTRIES), key = { it.entry.id }) { record ->
                        Column {
                            EntryRow(
                                record = record,
                                onClick = if (record.entry.isVoided) null else ({ onVoid(record) }),
                            )
                            HorizontalDivider()
                        }
                    }
                    item(key = "show-all") {
                        TextButton(onClick = onShowAll) {
                            Text(stringResource(R.string.dashboard_show_all))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DashboardSummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_cash_on_hand),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = displayMoney(summary.cashOnHand),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (summary.cashOnHand.isNegative) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = stringResource(R.string.dashboard_cash_on_hand_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            PeriodTotals(
                title = stringResource(R.string.dashboard_today),
                sales = summary.todaySales,
                expenses = summary.todayExpenses,
            )
            HorizontalDivider()
            PeriodTotals(
                title = stringResource(R.string.dashboard_this_month),
                sales = summary.monthSales,
                expenses = summary.monthExpenses,
            )
            LabeledAmount(
                label = stringResource(R.string.dashboard_month_net),
                amount = summary.monthNet,
            )
        }
    }
}

@Composable
private fun PeriodTotals(title: String, sales: Money, expenses: Money) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledAmount(
                label = stringResource(R.string.dashboard_sales),
                amount = sales,
                modifier = Modifier.weight(1f),
            )
            LabeledAmount(
                label = stringResource(R.string.dashboard_expenses),
                amount = expenses,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LabeledAmount(label: String, amount: Money, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = displayMoney(amount),
            style = MaterialTheme.typography.titleMedium,
            color = if (amount.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Sales and expenses get filled buttons, being the everyday entries; owner cash moves get outlined ones. */
@Composable
private fun EntryActions(onNewEntry: (EntryType) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onNewEntry(EntryType.SALE) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_action_sale))
            }
            Button(onClick = { onNewEntry(EntryType.EXPENSE) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_action_expense))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onNewEntry(EntryType.CAPITAL_IN) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_action_capital_in))
            }
            OutlinedButton(onClick = { onNewEntry(EntryType.CAPITAL_OUT) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_action_capital_out))
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun DashboardPreview() {
    val now = Instant.parse("2026-10-22T12:00:00Z")
    val records = listOf(
        LedgerRecord(
            entry = LedgerEntry("e2", EntryType.SALE, Money.ofMinor(1250, Currency.USD), now),
            createdBy = "preview",
            pendingSync = true,
        ),
        LedgerRecord(
            entry = LedgerEntry(
                id = "e1",
                type = EntryType.EXPENSE,
                amount = Money.ofMinor(4000, Currency.USD),
                occurredAt = now.minusSeconds(3_600),
                categoryId = "electricity",
                note = "اشتراك المولّد",
            ),
            createdBy = "preview",
            pendingSync = false,
        ),
    )
    ShopFinanceTheme {
        DashboardScreen(
            shop = Shop(id = "preview", name = "سوبرماركت الأمل", ownerUid = "preview", currency = Currency.USD),
            state = LedgerUiState.Ready(
                summary = DashboardSummary.of(records.map { it.entry }, now, java.time.ZoneOffset.UTC),
                records = records,
                pendingCount = 1,
            ),
            rejection = null,
            onDismissRejection = {},
            onNewEntry = {},
            onShowAll = {},
            onOpenDebts = {},
            onVoid = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}
