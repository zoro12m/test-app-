package com.shopfinance.app.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopfinance.app.R
import com.shopfinance.app.data.ledger.ExpenseCategory
import com.shopfinance.app.data.ledger.LedgerInputRules
import com.shopfinance.app.ui.common.supportingTextOrNull
import com.shopfinance.app.ui.theme.ShopFinanceTheme
import com.shopfinance.domain.ledger.EntryType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Records one entry; calls [onDone] when it is saved or the person goes back. */
@Composable
internal fun EntryFormScreen(
    viewModel: EntryFormViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
    EntryFormContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDateChange = viewModel::onDateChange,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::submit,
        onBack = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryFormContent(
    state: EntryFormState,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(newEntryTitle(state.type)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            newEntryHint(state.type)?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val amountError = state.amountError?.let { amountErrorMessage(it) }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.entry_amount_label)) },
                placeholder = { Text(stringResource(R.string.entry_amount_placeholder)) },
                suffix = { Text("$") },
                isError = amountError != null,
                supportingText = supportingTextOrNull(amountError),
                // Amounts read left to right, like the digits in them.
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.needsCategory) {
                CategoryPicker(
                    selected = state.category,
                    missing = state.categoryMissing,
                    onSelect = onCategoryChange,
                )
            }

            DateChooser(
                date = state.date,
                today = state.today,
                onDateChange = onDateChange,
                onPickOther = { showDatePicker = true },
            )

            val noteError = if (state.noteTooLong) {
                stringResource(R.string.entry_note_too_long, LedgerInputRules.NOTE_MAX_LENGTH)
            } else {
                null
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.entry_note_label)) },
                isError = noteError != null,
                supportingText = supportingTextOrNull(noteError),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.saveFailed) {
                Text(
                    text = stringResource(R.string.entry_save_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = onSave,
                enabled = !state.saved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (showDatePicker) {
        EntryDatePicker(
            initial = state.date,
            today = state.today,
            onPick = { date ->
                onDateChange(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun CategoryPicker(
    selected: ExpenseCategory?,
    missing: Boolean,
    onSelect: (ExpenseCategory) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.entry_category_label),
            style = MaterialTheme.typography.labelLarge,
        )
        for (category in ExpenseCategory.entries) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = category == selected,
                        onClick = { onSelect(category) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = category == selected, onClick = null)
                Text(
                    text = expenseCategoryLabel(category),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (missing) {
            Text(
                text = stringResource(R.string.entry_category_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The chosen day, with quick choices for today and yesterday and a calendar for older days. */
@Composable
private fun DateChooser(
    date: LocalDate,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onPickOther: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.entry_date_label),
            style = MaterialTheme.typography.labelLarge,
        )
        val dayName = when (date) {
            today -> stringResource(R.string.entry_date_today)
            today.minusDays(1) -> stringResource(R.string.entry_date_yesterday)
            else -> null
        }
        val formatted = formatter.format(date)
        Text(
            text = if (dayName != null) stringResource(R.string.entry_date_with_day, dayName, formatted) else formatted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onDateChange(today) }) {
                Text(stringResource(R.string.entry_date_today))
            }
            OutlinedButton(onClick = { onDateChange(today.minusDays(1)) }) {
                Text(stringResource(R.string.entry_date_yesterday))
            }
            TextButton(onClick = onPickOther) {
                Text(stringResource(R.string.entry_date_other))
            }
        }
    }
}

/**
 * A calendar limited to 2000..today. The picker works in UTC midnights, so dates are
 * converted at UTC to keep the same calendar day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePicker(
    initial: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.toUtcMillis()
    val selectableDates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = EARLIEST_YEAR..today.year,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) onPick(millis.toUtcLocalDate()) else onDismiss()
                },
            ) {
                Text(stringResource(R.string.entry_date_pick_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** Same as LedgerInputRules.EARLIEST_DATE's year. */
private const val EARLIEST_YEAR = 2000

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Preview(showBackground = true, locale = "ar")
@Composable
private fun EntryFormPreview() {
    ShopFinanceTheme {
        EntryFormContent(
            state = EntryFormState(
                type = EntryType.EXPENSE,
                today = LocalDate.of(2026, 10, 22),
                amountText = "40",
                category = ExpenseCategory.ELECTRICITY,
            ),
            onAmountChange = {},
            onCategoryChange = {},
            onDateChange = {},
            onNoteChange = {},
            onSave = {},
            onBack = {},
        )
    }
}
