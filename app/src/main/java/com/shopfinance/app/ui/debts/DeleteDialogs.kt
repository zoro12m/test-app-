package com.shopfinance.app.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.debts.PaymentRecord
import com.shopfinance.app.ui.common.displayMoney

/** Asks before deleting the debt of [card] and, with it, its payments. */
@Composable
internal fun DeleteDebtDialog(
    card: DebtCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val debt = card.account.debt
    val paymentCount = card.payments.size
    DeleteDialog(
        title = stringResource(R.string.debt_delete_title),
        question = stringResource(R.string.debt_delete_body, displayMoney(debt.amount), dayText(debt.debtDate)),
        detail = if (paymentCount > 0) pluralStringResource(R.plurals.debt_delete_payments, paymentCount, paymentCount) else null,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Asks before deleting [payment]; the debt's remaining goes back up by its amount. */
@Composable
internal fun DeletePaymentDialog(
    payment: PaymentRecord,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DeleteDialog(
        title = stringResource(R.string.payment_delete_title),
        question = stringResource(
            R.string.payment_delete_body,
            displayMoney(payment.payment.amount),
            dayText(payment.payment.paymentDate),
        ),
        detail = null,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun DeleteDialog(
    title: String,
    question: String,
    detail: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = question, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) {
                    Text(text = detail, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = stringResource(R.string.delete_keeps_record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
