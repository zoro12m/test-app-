package com.shopfinance.app.ui.ledger

import com.shopfinance.app.data.ledger.ExpenseCategory
import com.shopfinance.app.data.ledger.LedgerInputRules
import com.shopfinance.app.data.ledger.NewEntry
import com.shopfinance.app.ui.fakes.FakeLedgerRepository
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** [EntryFormViewModel] runs no coroutines (addEntry returns at once), so no test dispatcher is needed. */
class EntryFormViewModelTest {

    private val ledger = FakeLedgerRepository()
    private val beirut = ZoneId.of("Asia/Beirut")

    /** 22 October 2026, 00:30 in Beirut: still the 21st in UTC. */
    private val now = Instant.parse("2026-10-21T21:30:00Z")
    private val today = LocalDate.of(2026, 10, 22)

    private fun form(type: EntryType) =
        EntryFormViewModel(ledger, shopId = "s1", createdBy = "alice", type = type, clock = { now }, zone = { beirut })

    private val EntryFormViewModel.current: EntryFormState get() = state.value

    private val saved: NewEntry get() = ledger.addCalls.single().entry

    @Test
    fun startsEmptyOnTodayInThePhonesTimeZone() {
        assertEquals(EntryFormState(type = EntryType.SALE, today = today), form(EntryType.SALE).current)
    }

    @Test
    fun onlyCashTypesCanBeRecorded() {
        val refused = runCatching { form(EntryType.DEBT_GIVEN) }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException)
    }

    @Test
    fun aSaleIsRecordedForTheShopAndPersonAtTheCurrentTime() {
        val viewModel = form(EntryType.SALE)
        viewModel.onAmountChange("12.50")
        viewModel.onNoteChange("زبون من الحي")
        viewModel.submit()

        assertEquals("s1", ledger.addCalls.single().shopId)
        assertEquals("alice", ledger.addCalls.single().createdBy)
        assertEquals(
            NewEntry(EntryType.SALE, Money(1_250, Currency.USD), now, category = null, note = "زبون من الحي"),
            saved,
        )
        assertTrue(viewModel.current.saved)
    }

    @Test
    fun arabicDigitsAreAccepted() {
        val viewModel = form(EntryType.CAPITAL_IN)
        viewModel.onAmountChange("١٬٥٠٠٫٧٥")
        viewModel.submit()

        assertEquals(Money(150_075, Currency.USD), saved.amount)
    }

    @Test
    fun anEmptyAmountIsReportedAndNothingIsRecorded() {
        val viewModel = form(EntryType.SALE)
        viewModel.submit()

        assertEquals(AmountError.EMPTY, viewModel.current.amountError)
        assertFalse(viewModel.current.saved)
        assertTrue(ledger.addCalls.isEmpty())
    }

    @Test
    fun typingClearsTheAmountError() {
        val viewModel = form(EntryType.SALE)
        viewModel.submit()
        viewModel.onAmountChange("5")

        assertNull(viewModel.current.amountError)
    }

    @Test
    fun amountsAreReadExactly() {
        assertEquals(AmountInput.Valid(Money(1_250, Currency.USD)), EntryFormViewModel.parseAmount("12.5"))
        assertEquals(AmountInput.Valid(Money(1_250, Currency.USD)), EntryFormViewModel.parseAmount(" 12.50 "))
        assertEquals(AmountInput.Valid(Money(1, Currency.USD)), EntryFormViewModel.parseAmount("0.01"))
        assertEquals(
            AmountInput.Valid(Money(LedgerInputRules.MAX_AMOUNT_MINOR, Currency.USD)),
            EntryFormViewModel.parseAmount("100,000,000"),
        )
    }

    @Test
    fun badAmountsSayWhatIsWrong() {
        val cases = mapOf(
            "" to AmountError.EMPTY,
            "   " to AmountError.EMPTY,
            "abc" to AmountError.INVALID,
            "1,5" to AmountError.INVALID,
            "-5" to AmountError.NEGATIVE,
            "1.234" to AmountError.TOO_MANY_DECIMALS,
            "0" to AmountError.ZERO,
            "0.00" to AmountError.ZERO,
            "100000000.01" to AmountError.TOO_LARGE,
            "9".repeat(30) to AmountError.TOO_LARGE,
        )
        for ((text, error) in cases) {
            assertEquals("\"$text\"", AmountInput.Invalid(error), EntryFormViewModel.parseAmount(text))
        }
    }

    @Test
    fun anExpenseNeedsACategory() {
        val viewModel = form(EntryType.EXPENSE)
        viewModel.onAmountChange("40")
        viewModel.submit()

        assertTrue(viewModel.current.categoryMissing)
        assertTrue(ledger.addCalls.isEmpty())

        viewModel.onCategoryChange(ExpenseCategory.ELECTRICITY)
        assertFalse(viewModel.current.categoryMissing)
        viewModel.submit()

        assertEquals(ExpenseCategory.ELECTRICITY, saved.category)
        assertEquals(EntryType.EXPENSE, saved.type)
    }

    @Test
    fun onlyExpensesCarryACategory() {
        val viewModel = form(EntryType.SALE)
        viewModel.onAmountChange("3")
        viewModel.onCategoryChange(ExpenseCategory.RENT)
        viewModel.submit()

        assertNull(saved.category)
    }

    @Test
    fun anEarlierDayIsRecordedAtLocalNoon() {
        val viewModel = form(EntryType.SALE)
        viewModel.onAmountChange("8")
        viewModel.onDateChange(today.minusDays(1))
        viewModel.submit()

        assertEquals(Instant.parse("2026-10-21T09:00:00Z"), saved.occurredAt)
    }

    @Test
    fun daysAfterTodayAreIgnored() {
        val viewModel = form(EntryType.SALE)
        viewModel.onDateChange(today.plusDays(1))

        assertEquals(today, viewModel.current.date)
    }

    @Test
    fun aNoteOverTheLimitIsReportedAndNothingIsRecorded() {
        val viewModel = form(EntryType.SALE)
        viewModel.onAmountChange("1")
        viewModel.onNoteChange("ن".repeat(LedgerInputRules.NOTE_MAX_LENGTH + 1))
        viewModel.submit()

        assertTrue(viewModel.current.noteTooLong)
        assertTrue(ledger.addCalls.isEmpty())

        viewModel.onNoteChange("ن".repeat(LedgerInputRules.NOTE_MAX_LENGTH))
        assertFalse(viewModel.current.noteTooLong)
        viewModel.submit()
        assertEquals(1, ledger.addCalls.size)
    }

    @Test
    fun allProblemsAreShownAtOnce() {
        val viewModel = form(EntryType.EXPENSE)
        viewModel.onNoteChange("ن".repeat(LedgerInputRules.NOTE_MAX_LENGTH + 1))
        viewModel.submit()

        assertEquals(AmountError.EMPTY, viewModel.current.amountError)
        assertTrue(viewModel.current.categoryMissing)
        assertTrue(viewModel.current.noteTooLong)
    }

    @Test
    fun repeatedSubmitsRecordTheEntryOnlyOnce() {
        val viewModel = form(EntryType.SALE)
        viewModel.onAmountChange("2")
        viewModel.submit()
        viewModel.submit()

        assertEquals(1, ledger.addCalls.size)
    }

    @Test
    fun theFormCannotBeEditedOnceSaved() {
        val viewModel = form(EntryType.SALE)
        viewModel.onAmountChange("2")
        viewModel.submit()
        viewModel.onAmountChange("200")

        assertEquals("2", viewModel.current.amountText)
    }

    @Test
    fun aRefusedEntryIsReportedAndCanBeRetried() {
        ledger.addError = IllegalArgumentException("Invalid entry")
        val viewModel = form(EntryType.SALE)
        viewModel.onAmountChange("2")
        viewModel.submit()

        assertTrue(viewModel.current.saveFailed)
        assertFalse(viewModel.current.saved)

        viewModel.onAmountChange("3")
        assertFalse(viewModel.current.saveFailed)

        ledger.addError = null
        viewModel.submit()
        assertTrue(viewModel.current.saved)
        assertEquals(Money(300, Currency.USD), saved.amount)
    }
}
