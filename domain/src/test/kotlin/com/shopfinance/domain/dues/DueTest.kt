package com.shopfinance.domain.dues

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.CurrencyMismatchException
import com.shopfinance.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DueTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private val dueDate = LocalDate.of(2026, 11, 1)
    private val due = Due("d1", "c1", DueDirection.RECEIVABLE, usd(10_000), dueDate)

    @Test
    fun `status follows payments and the calendar`() {
        assertEquals(DueStatus.OPEN, due.statusOn(dueDate.minusDays(5)))
        assertEquals(DueStatus.OPEN, due.statusOn(dueDate)) // due today is not overdue yet
        assertEquals(DueStatus.OVERDUE, due.statusOn(dueDate.plusDays(1)))

        val partial = due.applyPayment(usd(4_000))
        assertEquals(DueStatus.PARTIAL, partial.statusOn(dueDate))
        assertEquals(DueStatus.OVERDUE, partial.statusOn(dueDate.plusDays(1)))

        val paid = partial.applyPayment(usd(6_000))
        assertEquals(DueStatus.PAID, paid.statusOn(dueDate))
        assertEquals(DueStatus.PAID, paid.statusOn(dueDate.plusYears(1)))
    }

    @Test
    fun `remaining tracks payments`() {
        val partial = due.applyPayment(usd(2_550))
        assertEquals(usd(2_550), partial.paid)
        assertEquals(usd(7_450), partial.remaining)
    }

    @Test
    fun `payments must be positive, same currency, and not more than remaining`() {
        assertFailsWith<IllegalArgumentException> { due.applyPayment(usd(0)) }
        assertFailsWith<IllegalArgumentException> { due.applyPayment(usd(-1)) }
        assertFailsWith<IllegalArgumentException> { due.applyPayment(usd(10_001)) }
        assertFailsWith<CurrencyMismatchException> { due.applyPayment(Money(5_000, Currency.LBP)) }
    }

    @Test
    fun `invalid dues are rejected`() {
        assertFailsWith<IllegalArgumentException> { due.copy(amount = usd(0)) }
        assertFailsWith<IllegalArgumentException> { due.copy(paid = usd(10_001)) }
        assertFailsWith<IllegalArgumentException> { due.copy(paid = usd(-1)) }
        assertFailsWith<IllegalArgumentException> { due.copy(partyId = "") }
        assertFailsWith<CurrencyMismatchException> { due.copy(paid = Money(0, Currency.LBP)) }
    }

    @Test
    fun `days until due`() {
        assertEquals(3, due.daysUntilDue(dueDate.minusDays(3)))
        assertEquals(0, due.daysUntilDue(dueDate))
        assertEquals(-2, due.daysUntilDue(dueDate.plusDays(2)))
    }
}
