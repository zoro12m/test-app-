package com.shopfinance.app.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.ledger.LedgerRecord
import com.shopfinance.app.data.ledger.VoidReasonError
import com.shopfinance.app.ui.common.displayMoney
import com.shopfinance.app.ui.common.supportingTextOrNull

/**
 * Asks for a reason, then voids [record]. [onConfirm] returns why the reason is not accepted,
 * or null when the void was queued, which closes the dialog.
 */
@Composable
internal fun VoidEntryDialog(
    record: LedgerRecord,
    onConfirm: (reason: String) -> VoidReasonError?,
    onDismiss: () -> Unit,
) {
    val entryId = record.entry.id
    var reason by rememberSaveable(entryId) { mutableStateOf("") }
    var error by remember(entryId) { mutableStateOf<VoidReasonError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.void_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${entryTypeLabel(record.entry.type)}: ${displayMoney(record.entry.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.void_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val errorMessage = error?.let { voidReasonErrorMessage(it) }
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.void_reason_label)) },
                    isError = errorMessage != null,
                    supportingText = supportingTextOrNull(errorMessage),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val problem = onConfirm(reason)
                    error = problem
                    if (problem == null) onDismiss()
                },
            ) {
                Text(
                    text = stringResource(R.string.void_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.void_dismiss))
            }
        },
    )
}
