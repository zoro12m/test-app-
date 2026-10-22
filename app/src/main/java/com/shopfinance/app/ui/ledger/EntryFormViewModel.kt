package com.shopfinance.app.ui.ledger

import androidx.lifecycle.ViewModel
import com.shopfinance.app.data.ledger.ExpenseCategory
import com.shopfinance.app.data.ledger.LedgerInputRules
import com.shopfinance.app.data.ledger.LedgerRepository
import com.shopfinance.app.data.ledger.NewEntry
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyParseError
import com.shopfinance.domain.money.MoneyParseResult
import com.shopfinance.domain.money.MoneyParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Why the typed amount is not accepted. */
enum class AmountError { EMPTY, INVALID, NEGATIVE, TOO_MANY_DECIMALS, ZERO, TOO_LARGE }

/** The typed amount, read as USD by [EntryFormViewModel.parseAmount]. */
sealed interface AmountInput {
    data class Valid(val money: Money) : AmountInput
    data class Invalid(val error: AmountError) : AmountInput
}

/** What the add-entry form shows. */
data class EntryFormState(
    val type: EntryType,
    val today: LocalDate,
    val date: LocalDate = today,
    val amountText: String = "",
    val amountError: AmountError? = null,
    val category: ExpenseCategory? = null,
    val categoryMissing: Boolean = false,
    val note: String = "",
    val noteTooLong: Boolean = false,
    /** The entry was handed to the repository; the screen closes. */
    val saved: Boolean = false,
    /** The repository refused the entry; nothing was stored. */
    val saveFailed: Boolean = false,
) {
    val needsCategory: Boolean get() = type == EntryType.EXPENSE
}

/**
 * Records one cash entry of [type] in the shop [shopId]. Amounts are typed in dollars (Western
 * or Arabic digits) and stored as exact cents. Saving happens at most once.
 */
class EntryFormViewModel(
    private val ledger: LedgerRepository,
    private val shopId: String,
    private val createdBy: String,
    type: EntryType,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    init {
        require(type in LedgerInputRules.SUPPORTED_TYPES) { "Unsupported entry type: $type" }
    }

    private val _state = MutableStateFlow(EntryFormState(type = type, today = today()))
    val state: StateFlow<EntryFormState> = _state.asStateFlow()

    fun onAmountChange(text: String) = edit { it.copy(amountText = text, amountError = null) }

    fun onCategoryChange(category: ExpenseCategory) = edit { it.copy(category = category, categoryMissing = false) }

    /** Picks the day the money moved. Days after today are ignored. */
    fun onDateChange(date: LocalDate) = edit { if (date.isAfter(it.today)) it else it.copy(date = date) }

    fun onNoteChange(text: String) = edit { it.copy(note = text, noteTooLong = false) }

    fun submit() {
        val form = _state.value
        if (form.saved) return

        val amount = parseAmount(form.amountText)
        val amountError = (amount as? AmountInput.Invalid)?.error
        val categoryMissing = form.needsCategory && form.category == null
        val noteTooLong = LedgerInputRules.normalizeNote(form.note).length > LedgerInputRules.NOTE_MAX_LENGTH
        if (amount !is AmountInput.Valid || categoryMissing || noteTooLong) {
            _state.update {
                it.copy(
                    amountError = amountError,
                    categoryMissing = categoryMissing,
                    noteTooLong = noteTooLong,
                    saveFailed = false,
                )
            }
            return
        }

        val entry = NewEntry(
            type = form.type,
            amount = amount.money,
            occurredAt = occurredAt(form.date, form.today),
            category = if (form.needsCategory) form.category else null,
            note = form.note,
        )
        try {
            // Returns at once: the write is queued locally and synced when online.
            ledger.addEntry(shopId, createdBy, entry)
            _state.update { it.copy(saved = true, saveFailed = false) }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(saveFailed = true) }
        }
    }

    private fun edit(change: (EntryFormState) -> EntryFormState) {
        _state.update { if (it.saved) it else change(it).copy(saveFailed = false) }
    }

    private fun today(): LocalDate = clock().atZone(zone()).toLocalDate()

    /** Today's entries keep the current time; an earlier day is recorded at noon, clear of time-zone edges. */
    private fun occurredAt(date: LocalDate, today: LocalDate): Instant =
        if (date == today) clock() else date.atTime(LocalTime.NOON).atZone(zone()).toInstant()

    companion object {
        /** The typed [text] as USD, or why it is not accepted. */
        internal fun parseAmount(text: String): AmountInput =
            when (val result = MoneyParser.parse(text, Currency.USD, RoundingMode.UNNECESSARY)) {
                is MoneyParseResult.Success -> when {
                    result.money.isZero -> AmountInput.Invalid(AmountError.ZERO)
                    result.money.minor > LedgerInputRules.MAX_AMOUNT_MINOR -> AmountInput.Invalid(AmountError.TOO_LARGE)
                    else -> AmountInput.Valid(result.money)
                }
                is MoneyParseResult.Failure -> AmountInput.Invalid(
                    when (result.error) {
                        MoneyParseError.EMPTY -> AmountError.EMPTY
                        MoneyParseError.INVALID_FORMAT -> AmountError.INVALID
                        MoneyParseError.NEGATIVE -> AmountError.NEGATIVE
                        MoneyParseError.TOO_MANY_DECIMALS -> AmountError.TOO_MANY_DECIMALS
                        MoneyParseError.OUT_OF_RANGE -> AmountError.TOO_LARGE
                    },
                )
            }
    }
}
