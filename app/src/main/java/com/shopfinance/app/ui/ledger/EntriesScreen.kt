package com.shopfinance.app.ui.ledger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.ledger.LedgerRecord

/** Every entry, newest first, voided ones included. Tapping one that is not voided offers to void it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntriesScreen(
    state: LedgerUiState,
    onBack: () -> Unit,
    onVoid: (LedgerRecord) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.entries_title)) },
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
            LedgerUiState.Loading -> LedgerLoading(modifier = contentModifier)
            is LedgerUiState.Error -> LedgerError(
                accessDenied = state.accessDenied,
                onRetry = onRetry,
                modifier = contentModifier,
            )
            is LedgerUiState.Ready -> if (state.records.isEmpty()) {
                Box(modifier = contentModifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.entries_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = contentModifier,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(state.records, key = { it.entry.id }) { record ->
                        Column {
                            EntryRow(
                                record = record,
                                onClick = if (record.entry.isVoided) null else ({ onVoid(record) }),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
