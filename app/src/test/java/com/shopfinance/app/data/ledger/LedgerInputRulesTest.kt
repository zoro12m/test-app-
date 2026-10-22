package com.shopfinance.app.data.ledger

import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LedgerInputRulesTest {

    private val now = Instant.parse("2026-10-22T09:30:00Z")

    private fun entry(
        type: EntryType = EntryType.SALE,
        cents: Long = 1_250,
        currency: Currency = Currency.USD,
        occurredAt: Instant = now,
        category: ExpenseCategory? = null,
        note: String = "",
    ) = NewEntry(type, Money(cents, currency), occurredAt, category, note)

    private fun validate(entry: NewEntry): NewEntryError? = LedgerInputRules.validate(entry, now)

    @Test
    fun everySupportedTypeIsAccepted() {
        assertNull(validate(entry(type = EntryType.SALE)))
        assertNull(validate(entry(type = EntryType.EXPENSE, category = ExpenseCategory.RENT)))
        assertNull(validate(entry(type = EntryType.CAPITAL_IN)))
        assertNull(validate(entry(type = EntryType.CAPITAL_OUT)))
    }

    @Test
    fun creditTypesAreNotRecordedYet() {
        for (type in EntryType.entries - LedgerInputRules.SUPPORTED_TYPES.toSet()) {
            assertEquals(type.name, NewEntryError.UNSUPPORTED_TYPE, validate(entry(type = type)))
        }
    }

    @Test
    fun onlyDollarsAreAccepted() {
        assertEquals(NewEntryError.NOT_USD, validate(entry(cents = 150_000, currency = Currency.LBP)))
    }

    @Test
    fun amountMustBePositive() {
        assertEquals(NewEntryError.NOT_POSITIVE, validate(entry(cents = 0)))
        assertEquals(NewEntryError.NOT_POSITIVE, validate(entry(cents = -1)))
        assertNull(validate(entry(cents = 1)))
    }

    @Test
    fun amountUpToTheMaximumIsAccepted() {
        assertNull(validate(entry(cents = LedgerInputRules.MAX_AMOUNT_MINOR)))
        assertEquals(NewEntryError.TOO_LARGE, validate(entry(cents = LedgerInputRules.MAX_AMOUNT_MINOR + 1)))
    }

    @Test
    fun expensesNeedACategoryAndNothingElseMayHaveOne() {
        assertEquals(NewEntryError.MISSING_CATEGORY, validate(entry(type = EntryType.EXPENSE)))
        assertEquals(
            NewEntryError.UNEXPECTED_CATEGORY,
            validate(entry(type = EntryType.SALE, category = ExpenseCategory.GOODS)),
        )
        assertEquals(
            NewEntryError.UNEXPECTED_CATEGORY,
            validate(entry(type = EntryType.CAPITAL_IN, category = ExpenseCategory.OTHER)),
        )
    }

    @Test
    fun noteLengthIsCheckedAfterNormalizing() {
        val longest = "ن".repeat(LedgerInputRules.NOTE_MAX_LENGTH)
        assertNull(validate(entry(note = longest)))
        assertNull(validate(entry(note = "   $longest \n")))
        assertEquals(NewEntryError.NOTE_TOO_LONG, validate(entry(note = longest + "ن")))
    }

    @Test
    fun entriesBefore2000AreRefused() {
        assertNull(validate(entry(occurredAt = LedgerInputRules.EARLIEST_DATE)))
        assertEquals(
            NewEntryError.TOO_EARLY,
            validate(entry(occurredAt = LedgerInputRules.EARLIEST_DATE.minusMillis(1))),
        )
    }

    @Test
    fun entriesMayBeAFewHoursAheadForClockDifferencesButNoMore() {
        assertNull(validate(entry(occurredAt = now.plus(LedgerInputRules.FUTURE_TOLERANCE))))
        assertEquals(
            NewEntryError.IN_THE_FUTURE,
            validate(entry(occurredAt = now.plus(LedgerInputRules.FUTURE_TOLERANCE).plusSeconds(1))),
        )
    }

    @Test
    fun theFutureMarginStaysInsideTheServersDayOfSlack() {
        assertTrue(LedgerInputRules.FUTURE_TOLERANCE < Duration.ofDays(1))
    }

    @Test
    fun voidReasonIsRequired() {
        assertEquals(VoidReasonError.EMPTY, LedgerInputRules.validateVoidReason(""))
        assertEquals(VoidReasonError.EMPTY, LedgerInputRules.validateVoidReason("  \n\t "))
        assertEquals(VoidReasonError.EMPTY, LedgerInputRules.validateVoidReason("\u0001\u0002"))
    }

    @Test
    fun voidReasonLengthIsCheckedAfterNormalizing() {
        val longest = "س".repeat(LedgerInputRules.VOID_REASON_MAX_LENGTH)
        assertNull(LedgerInputRules.validateVoidReason(longest))
        assertNull(LedgerInputRules.validateVoidReason("  $longest  "))
        assertEquals(VoidReasonError.TOO_LONG, LedgerInputRules.validateVoidReason(longest + "س"))
    }

    @Test
    fun voidReasonIsStoredOnOneTrimmedLine() {
        assertEquals("سُجّل مرتين", LedgerInputRules.normalizeVoidReason("  سُجّل\nمرتين "))
    }
}
