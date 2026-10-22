package com.shopfinance.app.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.debts.DebtRecord
import com.shopfinance.app.data.debts.DebtRejection
import com.shopfinance.app.data.debts.DebtorRecord
import com.shopfinance.app.data.debts.DebtsSnapshot
import com.shopfinance.app.data.debts.PaymentRecord
import com.shopfinance.app.ui.common.ErrorBanner
import com.shopfinance.app.ui.common.displayMoney
import com.shopfinance.app.ui.theme.ShopFinanceTheme
import com.shopfinance.domain.debts.Debt
import com.shopfinance.domain.debts.DebtPayment
import com.shopfinance.domain.debts.DebtStatus
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * One person's page: what they still owe per currency, then each debt with what was paid,
 * what remains and its payments. Open debts can take a payment; debts and payments can be
 * deleted after a confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtorScreen(
    state: DebtsUiState,
    debtorId: String,
    rejection: DebtRejection?,
    onDismissRejection: () -> Unit,
    onAddDebt: () -> Unit,
    onRecordPayment: (debtId: String) -> Unit,
    onDeleteDebt: (debtId: String) -> Unit,
    onDeletePayment: (paymentId: String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overview = (state as? DebtsUiState.Ready)?.overview
    val row = overview?.row(debtorId)
    var deletingDebtId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingPaymentId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = row?.debtor?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
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
            is DebtsUiState.Ready -> if (row == null) {
                Box(
                    modifier = contentModifier.padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.debtor_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val cards = state.overview.cards(row)
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
                    item(key = "header") { DebtorHeader(debtor = row.debtor, row = row) }
                    item(key = "add-debt") {
                        Button(onClick = onAddDebt, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.debtor_add_debt))
                        }
                    }
                    if (cards.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.debtor_no_debts),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(cards, key = { it.account.debt.id }) { card ->
                        DebtCardView(
                            card = card,
                            onRecordPayment = { onRecordPayment(card.account.debt.id) },
                            onDeleteDebt = { deletingDebtId = card.account.debt.id },
                            onDeletePayment = { paymentId -> deletingPaymentId = paymentId },
                        )
                    }
                }
            }
        }
    }

    val cards = if (overview != null && row != null) overview.cards(row) else emptyList()
    cards.firstOrNull { it.account.debt.id == deletingDebtId }?.let { card ->
        DeleteDebtDialog(
            card = card,
            onConfirm = {
                onDeleteDebt(card.account.debt.id)
                deletingDebtId = null
            },
            onDismiss = { deletingDebtId = null },
        )
    }
    cards.flatMap { it.payments }.firstOrNull { it.payment.id == deletingPaymentId }?.let { payment ->
        DeletePaymentDialog(
            payment = payment,
            onConfirm = {
                onDeletePayment(payment.payment.id)
                deletingPaymentId = null
            },
            onDismiss = { deletingPaymentId = null },
        )
    }
}

@Composable
private fun DebtorHeader(debtor: DebtorRecord, row: DebtorRow, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            debtor.phone?.let { phone ->
                Text(text = "\u2066$phone\u2069", style = MaterialTheme.typography.bodyMedium)
            }
            if (debtor.note.isNotEmpty()) {
                Text(
                    text = debtor.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.debtor_remaining),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (row.balance.remaining.isZero) {
                Text(
                    text = stringResource(if (row.balance.isSettled) R.string.debts_settled else R.string.debts_no_debts),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                for (amount in row.balance.remaining.toList()) {
                    Text(text = displayMoney(amount), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

/** A debt: its day and status, original, paid and remaining, then its payments. */
@Composable
private fun DebtCardView(
    card: DebtCard,
    onRecordPayment: () -> Unit,
    onDeleteDebt: () -> Unit,
    onDeletePayment: (paymentId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = card.account
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayText(account.debt.debtDate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = account.status)
            }
            Text(
                text = stringResource(
                    R.string.debt_original_and_paid,
                    displayMoney(account.debt.amount),
                    displayMoney(account.paid),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.debt_remaining, displayMoney(account.remaining)),
                style = MaterialTheme.typography.titleSmall,
            )
            account.overpaid?.let { extra ->
                Text(
                    text = stringResource(R.string.debt_overpaid, displayMoney(extra)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (account.debt.note.isNotEmpty()) {
                Text(
                    text = account.debt.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.record.pendingSync) {
                Text(
                    text = stringResource(R.string.debt_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.debt_payments),
                style = MaterialTheme.typography.labelLarge,
            )
            if (card.payments.isEmpty()) {
                Text(
                    text = stringResource(R.string.debt_no_payments),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (payment in card.payments) {
                PaymentLine(payment = payment, onDelete = { onDeletePayment(payment.payment.id) })
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (account.isOpen) {
                    OutlinedButton(onClick = onRecordPayment, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.debt_record_payment))
                    }
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDeleteDebt) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentLine(payment: PaymentRecord, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = dayText(payment.payment.paymentDate), style = MaterialTheme.typography.bodyMedium)
            if (payment.payment.note.isNotEmpty()) {
                Text(
                    text = payment.payment.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (payment.pendingSync) {
                Text(
                    text = stringResource(R.string.debt_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Text(text = displayMoney(payment.payment.amount), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.action_delete),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: DebtStatus) {
    val open = status == DebtStatus.OPEN
    Surface(
        color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = debtStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun DebtorPreview() {
    val createdAt = Instant.parse("2026-10-22T12:00:00Z")
    val snapshot = DebtsSnapshot(
        debtors = listOf(DebtorRecord("h", "هبة", "03 123 456", "", createdAt, pendingSync = false)),
        debts = listOf(
            DebtRecord(Debt("d1", "h", Money(4_000, Currency.USD), LocalDate.of(2026, 10, 9)), createdAt, false),
            DebtRecord(Debt("d2", "h", Money(500_000, Currency.LBP), LocalDate.of(2026, 10, 12)), createdAt, false),
            DebtRecord(Debt("d3", "h", Money(2_000, Currency.USD), LocalDate.of(2026, 9, 2)), createdAt, false),
        ),
        payments = listOf(
            PaymentRecord(DebtPayment("p1", "d1", Money(1_000, Currency.USD), LocalDate.of(2026, 10, 15)), createdAt, true),
            PaymentRecord(DebtPayment("p2", "d1", Money(500, Currency.USD), LocalDate.of(2026, 10, 11)), createdAt, false),
            PaymentRecord(DebtPayment("p3", "d3", Money(2_000, Currency.USD), LocalDate.of(2026, 9, 10)), createdAt, false),
        ),
        fromCache = false,
    )
    ShopFinanceTheme {
        DebtorScreen(
            state = DebtsUiState.Ready(DebtsOverview(snapshot, LocalDate.of(2026, 10, 22))),
            debtorId = "h",
            rejection = null,
            onDismissRejection = {},
            onAddDebt = {},
            onRecordPayment = {},
            onDeleteDebt = {},
            onDeletePayment = {},
            onRetry = {},
            onBack = {},
        )
    }
}
