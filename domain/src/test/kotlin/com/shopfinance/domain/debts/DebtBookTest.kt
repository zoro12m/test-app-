package com.shopfinance.domain.debts

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebtBookTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun lbp(minor: Long) = Money(minor, Currency.LBP)
    private fun day(text: String) = LocalDate.parse(text)

    private val today = day("2026-10-22")

    @Test
    fun `a person with USD and LBP debts has one total per currency`() {
        val debts = listOf(
            Debt("d1", "hiba", usd(4_000), day("2026-10-09")),
            Debt("d2", "hiba", lbp(500_000), day("2026-10-12")),
        )
        val payments = listOf(DebtPayment("p1", "d1", usd(1_500), day("2026-10-11")))

        val hiba = DebtBook.balances(listOf("hiba"), DebtBook.accounts(debts, payments)).getValue("hiba")

        assertEquals(listOf(usd(2_500), lbp(500_000)), hiba.remaining.toList())
        assertEquals(usd(2_500), hiba.remaining[Currency.USD])
        assertEquals(lbp(500_000), hiba.remaining[Currency.LBP])
    }

    @Test
    fun `the grand total keeps currencies apart`() {
        val debts = listOf(
            Debt("d1", "rami", usd(7_000), day("2026-10-01")),
            Debt("d2", "hiba", usd(2_500), day("2026-10-09")),
            Debt("d3", "hiba", lbp(500_000), day("2026-10-12")),
            Debt("d4", "karim", lbp(1_200_000), day("2026-10-18")),
        )
        val balances = DebtBook.balances(listOf("rami", "hiba", "karim"), DebtBook.accounts(debts, emptyList()))

        assertEquals(MoneyTotals.of(usd(9_500), lbp(1_700_000)), DebtBook.totalRemaining(balances.values))
    }

    @Test
    fun `paid debts stay in the person's history but not in what they owe`() {
        val debts = listOf(
            Debt("d1", "rami", usd(10_000), day("2026-10-01")),
            Debt("d2", "rami", usd(2_000), day("2026-09-02")),
        )
        val payments = listOf(
            DebtPayment("p1", "d1", usd(3_000), day("2026-10-05")),
            DebtPayment("p2", "d2", usd(2_000), day("2026-09-10")),
        )

        val rami = DebtBook.balances(listOf("rami"), DebtBook.accounts(debts, payments)).getValue("rami")

        assertEquals(listOf("d1", "d2"), rami.accounts.map { it.debt.id })
        assertEquals(MoneyTotals.of(usd(7_000)), rami.remaining)
        assertEquals(day("2026-10-01"), rami.oldestUnpaid)
        assertFalse(rami.isSettled)
    }

    @Test
    fun `a person whose debts are all paid is settled and still listed`() {
        val debts = listOf(Debt("d1", "rami", usd(10_000), day("2026-10-01")))
        val payments = listOf(
            DebtPayment("p1", "d1", usd(3_000), day("2026-10-05")),
            DebtPayment("p2", "d1", usd(7_000), day("2026-10-20")),
        )

        val rami = DebtBook.balances(listOf("rami"), DebtBook.accounts(debts, payments)).getValue("rami")

        assertTrue(rami.isSettled)
        assertTrue(rami.remaining.isZero)
        assertNull(rami.oldestUnpaid)
        assertNull(rami.daysSinceOldestUnpaid(today))
        assertEquals(DebtStatus.PAID, rami.accounts.single().status)
    }

    @Test
    fun `open debts come first oldest first, then paid debts newest first`() {
        val debts = listOf(
            Debt("open-late", "hiba", usd(100), day("2026-10-12")),
            Debt("paid-old", "hiba", usd(100), day("2026-08-01")),
            Debt("open-early", "hiba", usd(100), day("2026-10-09")),
            Debt("paid-new", "hiba", usd(100), day("2026-09-02")),
        )
        val payments = listOf(
            DebtPayment("p1", "paid-old", usd(100), day("2026-08-05")),
            DebtPayment("p2", "paid-new", usd(100), day("2026-09-05")),
        )

        val hiba = DebtBook.balances(listOf("hiba"), DebtBook.accounts(debts, payments)).getValue("hiba")

        assertEquals(listOf("open-early", "open-late", "paid-new", "paid-old"), hiba.accounts.map { it.debt.id })
        assertEquals(day("2026-10-09"), hiba.oldestUnpaid)
    }

    @Test
    fun `days since the oldest unpaid debt count calendar days`() {
        val debts = listOf(Debt("d1", "rami", usd(100), day("2026-09-28")))
        val rami = DebtBook.balances(listOf("rami"), DebtBook.accounts(debts, emptyList())).getValue("rami")

        assertEquals(24, rami.daysSinceOldestUnpaid(today))
        assertEquals(0, rami.daysSinceOldestUnpaid(day("2026-09-28")))
        assertEquals(3, rami.daysSinceOldestUnpaid(day("2026-10-01")))
        // A debt date ahead of the phone's clock counts as today, never as negative days.
        assertEquals(0, rami.daysSinceOldestUnpaid(day("2026-09-27")))
    }

    @Test
    fun `people with no debts yet get an empty balance`() {
        val balances = DebtBook.balances(listOf("new"), emptyList())
        val newPerson = balances.getValue("new")

        assertFalse(newPerson.hasDebts)
        assertFalse(newPerson.isSettled)
        assertTrue(newPerson.remaining.isZero)
    }

    @Test
    fun `payments without their debt are left out`() {
        val debts = listOf(Debt("d1", "rami", usd(10_000), day("2026-10-01")))
        val payments = listOf(
            DebtPayment("p1", "d1", usd(1_000), day("2026-10-05")),
            DebtPayment("p2", "deleted", usd(9_000), day("2026-10-05")),
        )

        val accounts = DebtBook.accounts(debts, payments)

        assertEquals(usd(9_000), accounts.single().remaining)
    }
}
