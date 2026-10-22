package com.shopfinance.app.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopfinance.app.R
import com.shopfinance.app.data.debts.DebtInputRules
import com.shopfinance.app.data.debts.NewDebtorError
import com.shopfinance.app.ui.common.displayMoney
import com.shopfinance.app.ui.common.supportingTextOrNull
import com.shopfinance.app.ui.ledger.AmountError
import com.shopfinance.app.ui.ledger.DateChooser
import com.shopfinance.app.ui.ledger.EntryDatePicker
import com.shopfinance.app.ui.ledger.amountErrorMessage
import com.shopfinance.domain.debts.PaymentProblem
import com.shopfinance.domain.money.Currency

/** Adds a person; calls [onSaved] with their new id, or [onBack] when the person goes back. */
@Composable
internal fun DebtorFormScreen(
    viewModel: DebtorFormViewModel,
    onSaved: (debtorId: String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) {
        state.savedId?.let(onSaved)
    }
    DebtFormScaffold(title = stringResource(R.string.debtor_new_title), onBack = onBack) {
        val nameError = when (state.nameError) {
            NewDebtorError.NAME_EMPTY -> stringResource(R.string.debtor_name_empty)
            NewDebtorError.NAME_TOO_LONG -> stringResource(R.string.debtor_name_too_long, DebtInputRules.NAME_MAX_LENGTH)
            else -> null
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.debtor_name_label)) },
            isError = nameError != null,
            supportingText = supportingTextOrNull(nameError),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val phoneError = if (state.phoneInvalid) {
            stringResource(R.string.debtor_phone_invalid, DebtInputRules.PHONE_MAX_LENGTH)
        } else {
            null
        }
        OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            label = { Text(stringResource(R.string.debtor_phone_label)) },
            isError = phoneError != null,
            supportingText = supportingTextOrNull(phoneError),
            // Phone numbers read left to right, like the digits in them.
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NoteField(value = state.note, tooLong = state.noteTooLong, onValueChange = viewModel::onNoteChange)
        SaveButton(saveFailed = state.saveFailed, enabled = state.savedId == null, onSave = viewModel::submit)
    }
}

/** Records a debt for [debtorName]; calls [onDone] when it is saved or the person goes back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtFormScreen(
    viewModel: DebtFormViewModel,
    debtorName: String?,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
    val title = if (debtorName != null) {
        stringResource(R.string.debt_new_title, debtorName)
    } else {
        stringResource(R.string.debt_new_title_plain)
    }
    DebtFormScaffold(title = title, onBack = onDone) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.debt_currency_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (currency in DebtInputRules.CURRENCIES) {
                    FilterChip(
                        selected = currency == state.currency,
                        onClick = { viewModel.onCurrencyChange(currency) },
                        label = { Text(currencyLabel(currency)) },
                    )
                }
            }
        }
        AmountField(
            text = state.amountText,
            currency = state.currency,
            error = state.amountError,
            extraError = null,
            onValueChange = viewModel::onAmountChange,
        )
        Text(
            text = stringResource(R.string.debt_date_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DateChooser(
            date = state.date,
            today = state.today,
            onDateChange = viewModel::onDateChange,
            onPickOther = { showDatePicker = true },
            label = stringResource(R.string.debt_date_label),
        )
        NoteField(value = state.note, tooLong = state.noteTooLong, onValueChange = viewModel::onNoteChange)
        SaveButton(saveFailed = state.saveFailed, enabled = !state.saved, onSave = viewModel::submit)
    }

    if (showDatePicker) {
        EntryDatePicker(
            initial = state.date,
            today = state.today,
            onPick = { date ->
                viewModel.onDateChange(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            earliest = DebtInputRules.EARLIEST_DAY,
        )
    }
}

/**
 * Records a payment on one debt, showing what remains with a "pay in full" shortcut. Calls
 * [onDone] when it is saved or the person goes back.
 */
@Composable
internal fun PaymentFormScreen(
    viewModel: PaymentFormViewModel,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
    val account = state.account
    val readError = state.readError
    DebtFormScaffold(title = stringResource(R.string.payment_new_title), onBack = onDone) {
        when {
            state.loading -> DebtsLoading()
            readError != null -> DebtsError(accessDenied = readError.accessDenied, onRetry = onRetry)
            account == null -> Text(
                text = stringResource(R.string.payment_debt_gone),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.payment_debt_summary,
                                dayText(account.debt.debtDate),
                                displayMoney(account.debt.amount),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.payment_remaining, displayMoney(account.remaining)),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                if (!account.isOpen) {
                    Text(
                        text = stringResource(R.string.payment_debt_paid),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    val problem = when (state.problem) {
                        PaymentProblem.NOT_POSITIVE -> stringResource(R.string.amount_error_zero)
                        PaymentProblem.MORE_THAN_REMAINING ->
                            stringResource(R.string.payment_more_than_remaining, displayMoney(account.remaining))
                        else -> null
                    }
                    AmountField(
                        text = state.amountText,
                        currency = account.debt.amount.currency,
                        error = state.amountError,
                        extraError = problem,
                        onValueChange = viewModel::onAmountChange,
                    )
                    OutlinedButton(onClick = viewModel::payInFull, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.payment_pay_in_full))
                    }
                    DateChooser(
                        date = state.date,
                        today = state.today,
                        onDateChange = viewModel::onDateChange,
                        onPickOther = { showDatePicker = true },
                        label = stringResource(R.string.payment_date_label),
                    )
                    val dateProblem = when (state.problem) {
                        PaymentProblem.BEFORE_DEBT_DATE ->
                            stringResource(R.string.payment_before_debt, dayText(account.debt.debtDate))
                        PaymentProblem.IN_THE_FUTURE -> stringResource(R.string.payment_in_future)
                        else -> null
                    }
                    if (dateProblem != null) {
                        Text(
                            text = dateProblem,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    NoteField(value = state.note, tooLong = state.noteTooLong, onValueChange = viewModel::onNoteChange)
                    SaveButton(saveFailed = state.saveFailed, enabled = !state.saved, onSave = viewModel::submit)
                }
            }
        }
    }

    if (showDatePicker && account != null) {
        EntryDatePicker(
            initial = state.date,
            today = state.today,
            onPick = { date ->
                viewModel.onDateChange(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            earliest = minOf(account.debt.debtDate, state.today),
        )
    }
}

/** A form page with a back button in the top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtFormScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            content = content,
        )
    }
}

/** The amount in [currency]; [extraError] is a problem found against the debt, shown like a field error. */
@Composable
private fun AmountField(
    text: String,
    currency: Currency,
    error: AmountError?,
    extraError: String?,
    onValueChange: (String) -> Unit,
) {
    val message = error?.let { amountErrorMessage(it, currency) } ?: extraError
    OutlinedTextField(
        value = text,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.debt_amount_label)) },
        placeholder = {
            Text(
                stringResource(
                    if (currency == Currency.LBP) R.string.debt_amount_placeholder_lbp else R.string.debt_amount_placeholder_usd,
                ),
            )
        },
        suffix = { Text(currency.symbol) },
        isError = message != null,
        supportingText = supportingTextOrNull(message),
        // Amounts read left to right, like the digits in them.
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (currency.minorDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NoteField(value: String, tooLong: Boolean, onValueChange: (String) -> Unit) {
    val error = if (tooLong) stringResource(R.string.entry_note_too_long, DebtInputRules.NOTE_MAX_LENGTH) else null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.entry_note_label)) },
        isError = error != null,
        supportingText = supportingTextOrNull(error),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SaveButton(saveFailed: Boolean, enabled: Boolean, onSave: () -> Unit) {
    if (saveFailed) {
        Text(
            text = stringResource(R.string.debts_save_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_save))
    }
}

@Composable
private fun currencyLabel(currency: Currency): String = when (currency) {
    Currency.USD -> stringResource(R.string.currency_short_usd)
    Currency.LBP -> stringResource(R.string.currency_short_lbp)
}
