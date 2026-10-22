package com.shopfinance.app.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.ledger.ExpenseCategory
import com.shopfinance.app.data.ledger.LedgerRecord
import com.shopfinance.app.ui.common.displaySignedMoney
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One ledger entry in a list: what it was, when, its note, and the amount signed by the way
 * the cash moved. Voided entries are struck through with their reason; entries the phone has
 * not sent yet say so. [onClick] is null for rows that cannot be acted on.
 */
@Composable
internal fun EntryRow(
    record: LedgerRecord,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val entry = record.entry
    val voided = entry.voided
    val zone = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    val decoration = if (voided != null) TextDecoration.LineThrough else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val category = ExpenseCategory.fromWireName(entry.categoryId)
            val title = if (category != null) {
                "${entryTypeLabel(entry.type)} · ${expenseCategoryLabel(category)}"
            } else {
                entryTypeLabel(entry.type)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = decoration,
            )
            Text(
                text = formatter.format(entry.occurredAt.atZone(zone)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.note.isNotEmpty()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (voided != null) {
                Text(
                    text = stringResource(R.string.entry_voided, voided.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (record.pendingSync) {
                Text(
                    text = stringResource(R.string.entry_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        val direction = entry.type.cash
        Text(
            text = displaySignedMoney(entry.amount, direction),
            style = MaterialTheme.typography.titleMedium,
            textDecoration = decoration,
            color = when {
                voided != null -> MaterialTheme.colorScheme.onSurfaceVariant
                direction > 0 -> MaterialTheme.colorScheme.primary
                direction < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** How many entries are still waiting to reach the server. */
@Composable
internal fun PendingNotice(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(R.string.ledger_pending, count),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** A centred spinner while the entries load. */
@Composable
internal fun LedgerLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.ledger_loading),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Reading the entries failed; [accessDenied] when Firestore refused, usually unpublished rules. */
@Composable
internal fun LedgerError(
    accessDenied: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (accessDenied) R.string.ledger_error_access_denied else R.string.ledger_error_generic,
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
