package com.shopfinance.domain.debts

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.CurrencyMismatchException
import com.shopfinance.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DebtAccountTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun lbp(minor: Long) = Money(minor, Currency.LBP)

    private val debtDate = LocalDate.of(2026, 10, 1)
    private val today = LocalDate.of(2026, 10, 22)
    private val debt = Debt("d1", "rami", usd(10_000), debtDate)
    private val fresh = DebtAccount(debt, emptyList())

    private fun payment(id: String, minor: Long, date: LocalDate = today) = DebtPayment(id, "d1", usd(minor), date)

    @Test
    fun `a debt with no payments is open for its full amount`() {
        assertEquals(usd(0), fresh.paid)
        assertEquals(usd(10_000), fresh.remaining)
        assertEquals(DebtStatus.OPEN, fresh.status)
        assertNull(fresh.overpaid)
    }

    @Test
    fun `paying 30 of 100 leaves 70 and the debt open`() {
        val account = fresh.record(payment("p1", 3_000, LocalDate.of(2026, 10, 5)), today)

        assertEquals(usd(3_000), account.paid)
        assertEquals(usd(7_000), account.remaining)
        assertEquals(DebtStatus.OPEN, account.status)
    }

    @Test
    fun `paying the last 70 leaves 0 and the debt paid`() {
        val account = fresh
            .record(payment("p1", 3_000, LocalDate.of(2026, 10, 5)), today)
            .record(payment("p2", 7_000, LocalDate.of(2026, 10, 20)), today)

        assertEquals(usd(10_000), account.paid)
        assertEquals(usd(0), account.remaining)
        assertEquals(DebtStatus.PAID, account.status)
        assertEquals(listOf("p2", "p1"), account.payments.map { it.id })
    }

    @Test
    fun `paying 80 when 70 remains is refused and changes nothing`() {
        val account = fresh.record(payment("p1", 3_000), today)

        assertEquals(PaymentProblem.MORE_THAN_REMAINING, account.checkPayment(usd(8_000), today, today))
        assertFailsWith<IllegalArgumentException> { account.record(payment("p2", 8_000), today) }
        assertEquals(usd(7_000), account.remaining)
        assertEquals(listOf("p1"), account.payments.map { it.id })
    }

    @Test
    fun `exactly the remaining is accepted`() {
        val account = fresh.record(payment("p1", 3_000), today)

        assertNull(account.checkPayment(usd(7_000), today, today))
        assertNull(account.checkPayment(usd(1), today, today))
    }

    @Test
    fun `zero or negative payments are refused`() {
        assertEquals(PaymentProblem.NOT_POSITIVE, fresh.checkPayment(usd(0), today, today))
        assertEquals(PaymentProblem.NOT_POSITIVE, fresh.checkPayment(usd(-500), today, today))
    }

    @Test
    fun `nothing more can be paid on a paid debt`() {
        val paid = fresh.record(payment("p1", 10_000), today)

        assertEquals(PaymentProblem.MORE_THAN_REMAINING, paid.checkPayment(usd(1), today, today))
    }

    @Test
    fun `payment dates must fall between the debt date and today`() {
        assertEquals(PaymentProblem.BEFORE_DEBT_DATE, fresh.checkPayment(usd(100), debtDate.minusDays(1), today))
        assertNull(fresh.checkPayment(usd(100), debtDate, today))
        assertNull(fresh.checkPayment(usd(100), today, today))
        assertEquals(PaymentProblem.IN_THE_FUTURE, fresh.checkPayment(usd(100), today.plusDays(1), today))
    }

    @Test
    fun `payments in another currency are a programming error`() {
        assertFailsWith<CurrencyMismatchException> { fresh.checkPayment(lbp(100), today, today) }
        assertFailsWith<CurrencyMismatchException> {
            DebtAccount(debt, listOf(DebtPayment("p1", "d1", lbp(100), today)))
        }
    }

    @Test
    fun `payments of another debt are refused`() {
        assertFailsWith<IllegalArgumentException> {
            DebtAccount(debt, listOf(DebtPayment("p1", "d2", usd(100), today)))
        }
    }

    @Test
    fun `payments that together exceed the debt show as overpaid`() {
        // Two phones offline at once can each record a payment that fits on its own.
        val account = DebtAccount(debt, listOf(payment("p1", 7_000), payment("p2", 5_000)))

        assertEquals(usd(-2_000), account.remaining)
        assertEquals(DebtStatus.PAID, account.status)
        assertEquals(usd(2_000), account.overpaid)
    }

    @Test
    fun `LBP debts work in whole lira`() {
        val lbpDebt = Debt("d2", "rami", lbp(500_000), debtDate)
        val account = DebtAccount(lbpDebt, listOf(DebtPayment("p1", "d2", lbp(150_000), today)))

        assertEquals(lbp(350_000), account.remaining)
        assertEquals(DebtStatus.OPEN, account.status)
    }

    @Test
    fun `invalid debts and payments are rejected`() {
        assertFailsWith<IllegalArgumentException> { debt.copy(amount = usd(0)) }
        assertFailsWith<IllegalArgumentException> { debt.copy(debtorId = " ") }
        assertFailsWith<IllegalArgumentException> { DebtPayment("p1", "d1", usd(0), today) }
        assertFailsWith<IllegalArgumentException> { DebtPayment("", "d1", usd(1), today) }
    }
}
