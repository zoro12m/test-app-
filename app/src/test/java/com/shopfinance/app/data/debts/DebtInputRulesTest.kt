package com.shopfinance.app.data.debts

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtInputRulesTest {

    private val today = LocalDate.of(2026, 10, 22)
    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun debt(amount: Money = usd(10_000), date: LocalDate = today, note: String = "") =
        NewDebt("debtor-1", amount, date, note)

    @Test
    fun namesAreTrimmedAndLimited() {
        assertEquals("رامي حداد", DebtInputRules.normalizeName("  رامي\n  حداد "))
        assertEquals(NewDebtorError.NAME_EMPTY, DebtInputRules.nameError(" \t"))
        assertNull(DebtInputRules.nameError("ر".repeat(DebtInputRules.NAME_MAX_LENGTH)))
        assertEquals(NewDebtorError.NAME_TOO_LONG, DebtInputRules.nameError("ر".repeat(DebtInputRules.NAME_MAX_LENGTH + 1)))
    }

    @Test
    fun phonesAreStoredWithWesternDigits() {
        assertEquals("03 123 456", DebtInputRules.normalizePhone(" ٠٣ ١٢٣ ٤٥٦ "))
        assertEquals("03123456", DebtInputRules.normalizePhone("۰۳۱۲۳۴۵۶"))
        assertEquals("+961 3 123 456", DebtInputRules.normalizePhone("+961  3 123 456"))
    }

    @Test
    fun phonesAreOptionalButMustLookLikeANumber() {
        assertTrue(DebtInputRules.isValidPhone(""))
        assertTrue(DebtInputRules.isValidPhone("   "))
        assertTrue(DebtInputRules.isValidPhone("03-123-456"))
        assertTrue(DebtInputRules.isValidPhone("٠٣١٢٣٤٥٦"))
        assertTrue(DebtInputRules.isValidPhone("1".repeat(DebtInputRules.PHONE_MAX_LENGTH)))
        assertFalse(DebtInputRules.isValidPhone("1".repeat(DebtInputRules.PHONE_MAX_LENGTH + 1)))
        assertFalse(DebtInputRules.isValidPhone("03 call me"))
        assertFalse(DebtInputRules.isValidPhone("+ -"))
    }

    @Test
    fun aDebtorIsCheckedFieldByField() {
        assertNull(DebtInputRules.validateDebtor(NewDebtor("رامي", "", "")))
        assertEquals(NewDebtorError.NAME_EMPTY, DebtInputRules.validateDebtor(NewDebtor(" ", "03", "")))
        assertEquals(NewDebtorError.PHONE_INVALID, DebtInputRules.validateDebtor(NewDebtor("رامي", "abc", "")))
        assertEquals(
            NewDebtorError.NOTE_TOO_LONG,
            DebtInputRules.validateDebtor(NewDebtor("رامي", "", "ن".repeat(DebtInputRules.NOTE_MAX_LENGTH + 1))),
        )
    }

    @Test
    fun debtsTakeEitherCurrency() {
        assertNull(DebtInputRules.validateDebt(debt(), today))
        assertNull(DebtInputRules.validateDebt(debt(amount = Money(500_000, Currency.LBP)), today))
    }

    @Test
    fun debtAmountsArePositiveAndCapped() {
        assertEquals(NewDebtError.NOT_POSITIVE, DebtInputRules.validateDebt(debt(amount = usd(0)), today))
        assertNull(DebtInputRules.validateDebt(debt(amount = usd(DebtInputRules.MAX_AMOUNT_MINOR)), today))
        assertEquals(
            NewDebtError.TOO_LARGE,
            DebtInputRules.validateDebt(debt(amount = Money(DebtInputRules.MAX_AMOUNT_MINOR + 1, Currency.LBP)), today),
        )
    }

    @Test
    fun debtDatesRunFrom2000ToToday() {
        assertNull(DebtInputRules.validateDebt(debt(date = LocalDate.of(2000, 1, 1)), today))
        assertNull(DebtInputRules.validateDebt(debt(date = LocalDate.of(2026, 10, 1)), today))
        assertEquals(NewDebtError.TOO_EARLY, DebtInputRules.validateDebt(debt(date = LocalDate.of(1999, 12, 31)), today))
        assertEquals(NewDebtError.IN_THE_FUTURE, DebtInputRules.validateDebt(debt(date = today.plusDays(1)), today))
    }

    @Test
    fun debtNotesAreLimited() {
        assertNull(DebtInputRules.validateDebt(debt(note = "ن".repeat(DebtInputRules.NOTE_MAX_LENGTH)), today))
        assertEquals(
            NewDebtError.NOTE_TOO_LONG,
            DebtInputRules.validateDebt(debt(note = "ن".repeat(DebtInputRules.NOTE_MAX_LENGTH + 1)), today),
        )
    }
}
