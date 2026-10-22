package com.shopfinance.app.ui.debts

import androidx.lifecycle.ViewModel
import com.shopfinance.app.data.debts.DebtInputRules
import com.shopfinance.app.data.debts.DebtRepository
import com.shopfinance.app.data.debts.NewDebt
import com.shopfinance.app.data.debts.NewDebtor
import com.shopfinance.app.data.debts.NewDebtorError
import com.shopfinance.app.data.debts.NewPayment
import com.shopfinance.app.ui.ledger.AmountError
import com.shopfinance.app.ui.ledger.AmountInput
import com.shopfinance.app.ui.ledger.parseMoneyInput
import com.shopfinance.domain.debts.DebtAccount
import com.shopfinance.domain.debts.PaymentProblem
import com.shopfinance.domain.money.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** What the add-debtor form shows. */
data class DebtorFormState(
    val name: String = "",
    val phone: String = "",
    val note: String = "",
    val nameError: NewDebtorError? = null,
    val phoneInvalid: Boolean = false,
    val noteTooLong: Boolean = false,
    /** The new person's id once they were handed to the repository; the screen opens their page. */
    val savedId: String? = null,
    /** The repository refused the person; nothing was stored. */
    val saveFailed: Boolean = false,
)

/** Adds one person who owes the shop money. Saving happens at most once. */
class DebtorFormViewModel(
    private val debts: DebtRepository,
    private val shopId: String,
    private val createdBy: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DebtorFormState())
    val state: StateFlow<DebtorFormState> = _state.asStateFlow()

    fun onNameChange(text: String) = edit { it.copy(name = text, nameError = null) }

    fun onPhoneChange(text: String) = edit { it.copy(phone = text, phoneInvalid = false) }

    fun onNoteChange(text: String) = edit { it.copy(note = text, noteTooLong = false) }

    fun submit() {
        val form = _state.value
        if (form.savedId != null) return

        val nameError = DebtInputRules.nameError(form.name)
        val phoneInvalid = !DebtInputRules.isValidPhone(form.phone)
        val noteTooLong = DebtInputRules.noteTooLong(form.note)
        if (nameError != null || phoneInvalid || noteTooLong) {
            _state.update {
                it.copy(nameError = nameError, phoneInvalid = phoneInvalid, noteTooLong = noteTooLong, saveFailed = false)
            }
            return
        }
        try {
            // Returns at once: the write is queued locally and synced when online.
            val id = debts.addDebtor(shopId, createdBy, NewDebtor(form.name, form.phone, form.note))
            _state.update { it.copy(savedId = id, saveFailed = false) }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(saveFailed = true) }
        }
    }

    private fun edit(change: (DebtorFormState) -> DebtorFormState) {
        _state.update { if (it.savedId != null) it else change(it).copy(saveFailed = false) }
    }
}

/** What the add-debt form shows. */
data class DebtFormState(
    val today: LocalDate,
    val currency: Currency = Currency.USD,
    val amountText: String = "",
    val amountError: AmountError? = null,
    /** The day the person took the money: today unless changed, any day back to 2000. */
    val date: LocalDate = today,
    val note: String = "",
    val noteTooLong: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Records one debt of the person [debtorId], in USD or LBP. Saving happens at most once. */
class DebtFormViewModel(
    private val debts: DebtRepository,
    private val shopId: String,
    private val createdBy: String,
    private val debtorId: String,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow(DebtFormState(today = today()))
    val state: StateFlow<DebtFormState> = _state.asStateFlow()

    fun onCurrencyChange(currency: Currency) = edit {
        if (currency in DebtInputRules.CURRENCIES) it.copy(currency = currency, amountError = null) else it
    }

    fun onAmountChange(text: String) = edit { it.copy(amountText = text, amountError = null) }

    /** Picks the day the money was taken. Days after today or before 2000 are ignored. */
    fun onDateChange(date: LocalDate) = edit {
        if (date.isAfter(it.today) || date.isBefore(DebtInputRules.EARLIEST_DAY)) it else it.copy(date = date)
    }

    fun onNoteChange(text: String) = edit { it.copy(note = text, noteTooLong = false) }

    fun submit() {
        val form = _state.value
        if (form.saved) return

        val amount = parseMoneyInput(form.amountText, form.currency, DebtInputRules.MAX_AMOUNT_MINOR)
        val noteTooLong = DebtInputRules.noteTooLong(form.note)
        if (amount !is AmountInput.Valid || noteTooLong) {
            _state.update {
                it.copy(amountError = (amount as? AmountInput.Invalid)?.error, noteTooLong = noteTooLong, saveFailed = false)
            }
            return
        }
        try {
            debts.addDebt(shopId, createdBy, NewDebt(debtorId, amount.money, form.date, form.note))
            _state.update { it.copy(saved = true, saveFailed = false) }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(saveFailed = true) }
        }
    }

    private fun edit(change: (DebtFormState) -> DebtFormState) {
        _state.update { if (it.saved) it else change(it).copy(saveFailed = false) }
    }
}

/** What the record-payment form shows. */
data class PaymentFormState(
    val today: LocalDate,
    /** True until the debt has been read. */
    val loading: Boolean = true,
    /** The debt as the phone sees it now, payments not yet synced included; null once it is gone. */
    val account: DebtAccount? = null,
    /** Reading the debts failed; [account] is unknown then, not gone. */
    val readError: DebtsUiState.Error? = null,
    val amountText: String = "",
    val amountError: AmountError? = null,
    /** Why the payment does not fit the debt, for example more than the remaining. */
    val problem: PaymentProblem? = null,
    val date: LocalDate = today,
    val note: String = "",
    val noteTooLong: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
)

/**
 * Records one payment against the debt [debtId]. The screen hands it the shop's live debts
 * ([onDebtsChange]), so the remaining shown and checked is always current. A payment that is
 * zero, more than the remaining, or dated outside the debt date..today is refused and nothing
 * is saved. Saving happens at most once.
 */
class PaymentFormViewModel(
    private val debts: DebtRepository,
    private val shopId: String,
    private val createdBy: String,
    private val debtId: String,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentFormState(today = today()))
    val state: StateFlow<PaymentFormState> = _state.asStateFlow()

    /**
     * Takes the latest read of the shop's debts, from the same [DebtsViewModel] as the debtors
     * list, so the form opens no listener of its own.
     */
    fun onDebtsChange(debts: DebtsUiState) = _state.update {
        when (debts) {
            DebtsUiState.Loading -> it
            is DebtsUiState.Ready -> it.copy(loading = false, account = debts.overview.account(debtId), readError = null)
            is DebtsUiState.Error -> it.copy(loading = false, readError = debts)
        }
    }

    fun onAmountChange(text: String) = edit { it.copy(amountText = text, amountError = null, problem = null) }

    /** Fills in everything that is still owed. */
    fun payInFull() = edit { form ->
        val remaining = form.account?.takeIf { it.isOpen }?.remaining ?: return@edit form
        form.copy(amountText = remaining.toMajor().toPlainString(), amountError = null, problem = null)
    }

    /** Picks the payment day. Days after today are ignored; a day before the debt is refused on save. */
    fun onDateChange(date: LocalDate) = edit {
        if (date.isAfter(it.today)) it else it.copy(date = date, problem = null)
    }

    fun onNoteChange(text: String) = edit { it.copy(note = text, noteTooLong = false) }

    fun submit() {
        val form = _state.value
        if (form.saved || form.readError != null) return
        val account = form.account ?: return

        val amount = parseMoneyInput(form.amountText, account.debt.amount.currency, DebtInputRules.MAX_AMOUNT_MINOR)
        val noteTooLong = DebtInputRules.noteTooLong(form.note)
        val problem = (amount as? AmountInput.Valid)?.let { account.checkPayment(it.money, form.date, form.today) }
        if (amount !is AmountInput.Valid || problem != null || noteTooLong) {
            _state.update {
                it.copy(
                    amountError = (amount as? AmountInput.Invalid)?.error,
                    problem = problem,
                    noteTooLong = noteTooLong,
                    saveFailed = false,
                )
            }
            return
        }
        try {
            debts.addPayment(shopId, createdBy, account, NewPayment(amount.money, form.date, form.note))
            _state.update { it.copy(saved = true, saveFailed = false) }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(saveFailed = true) }
        }
    }

    private fun edit(change: (PaymentFormState) -> PaymentFormState) {
        _state.update { if (it.saved) it else change(it).copy(saveFailed = false) }
    }
}
