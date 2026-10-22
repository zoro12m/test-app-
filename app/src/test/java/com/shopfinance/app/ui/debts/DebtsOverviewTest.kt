package com.shopfinance.app.ui.debts

import com.shopfinance.app.data.debts.DebtsSnapshot
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.debt
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.debtor
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.payment
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.snapshot
import com.shopfinance.domain.debts.DebtStatus
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtsOverviewTest {

    private val today = LocalDate.of(2026, 10, 22)

    private fun usd(dollars: Long) = Money(dollars * 100, Currency.USD)

    private fun lbp(lira: Long) = Money(lira, Currency.LBP)

    private fun overview(snapshot: DebtsSnapshot) = DebtsOverview(snapshot, today)

    private fun DebtsOverview.names(filter: DebtorFilter, query: String = "") = rows(filter, query).map { it.debtor.name }

    private val rami = debtor("r", "رامي")
    private val loan = debt("d1", "r", usd(100), "2026-10-01")

    @Test
    fun aPartPaymentLeavesTheDebtOpenWithTheRest() {
        val overview = overview(snapshot(listOf(rami), listOf(loan), listOf(payment("p1", "d1", usd(30), "2026-10-10"))))

        val row = overview.row("r")!!
        val card = overview.cards(row).single()
        assertEquals(usd(30), card.account.paid)
        assertEquals(usd(70), card.account.remaining)
        assertEquals(DebtStatus.OPEN, card.account.status)
        assertEquals(MoneyTotals.of(usd(70)), row.balance.remaining)
        assertEquals(listOf("رامي"), overview.names(DebtorFilter.OPEN))
        assertEquals(emptyList<String>(), overview.names(DebtorFilter.PAID))
        assertEquals(MoneyTotals.of(usd(70)), overview.totalRemaining)
    }

    @Test
    fun aFullyPaidDebtMovesToPaidAndStaysOnThePage() {
        val payments = listOf(
            payment("p1", "d1", usd(30), "2026-10-10"),
            payment("p2", "d1", usd(70), "2026-10-20"),
        )
        val overview = overview(snapshot(listOf(rami), listOf(loan), payments))

        val row = overview.row("r")!!
        val card = overview.cards(row).single()
        assertEquals(Money.zero(Currency.USD), card.account.remaining)
        assertEquals(DebtStatus.PAID, card.account.status)
        // Newest payment first, both still listed.
        assertEquals(listOf("p2", "p1"), card.payments.map { it.payment.id })
        assertTrue(row.balance.remaining.isZero)
        assertNull(row.daysSinceOldestUnpaid)

        assertEquals(emptyList<String>(), overview.names(DebtorFilter.OPEN))
        assertEquals(listOf("رامي"), overview.names(DebtorFilter.PAID))
        assertEquals(listOf("رامي"), overview.names(DebtorFilter.ALL))
        assertTrue(overview.totalRemaining.isZero)
    }

    @Test
    fun dollarsAndLiraAreTotalledSeparately() {
        val debts = listOf(
            debt("d1", "r", usd(100), "2026-10-01"),
            debt("d2", "r", lbp(500_000), "2026-10-05"),
            debt("d3", "h", usd(25), "2026-10-09"),
            debt("d4", "h", lbp(1_200_000), "2026-10-12"),
        )
        val overview = overview(snapshot(listOf(rami, debtor("h", "هبة")), debts, listOf(payment("p1", "d1", usd(30), "2026-10-10"))))

        val ramiOwes = overview.row("r")!!.balance.remaining
        assertEquals(listOf(usd(70), lbp(500_000)), ramiOwes.toList())
        assertEquals(listOf(usd(95), lbp(1_700_000)), overview.totalRemaining.toList())
    }

    @Test
    fun theOldestUnpaidDebtComesFirstThenEveryoneElseByName() {
        val debtors = listOf(debtor("a", "أمل"), debtor("b", "باسم"), debtor("c", "كريم"), debtor("z", "زياد"), debtor("n", "نور"))
        val debts = listOf(
            debt("d1", "a", usd(10), "2026-10-05"),
            debt("d2", "b", usd(10), "2026-10-15"),
            debt("d3", "b", usd(10), "2026-09-30"),
            debt("d4", "c", usd(10), "2026-08-01"),
            debt("d5", "z", lbp(90_000), "2026-10-20"),
        )
        // Karim's old debt is paid, so it no longer counts as unpaid.
        val overview = overview(snapshot(debtors, debts, listOf(payment("p1", "d4", usd(10), "2026-09-01"))))

        assertEquals(listOf("باسم", "أمل", "زياد", "كريم", "نور"), overview.rows.map { it.debtor.name })
        assertEquals(listOf(22L, 17L, 2L), overview.rows(DebtorFilter.OPEN, "").map { it.daysSinceOldestUnpaid })
        assertEquals(LocalDate.of(2026, 9, 30), overview.row("b")!!.balance.oldestUnpaid)
    }

    @Test
    fun theFilterSplitsOwingSettledAndEveryone() {
        val debtors = listOf(debtor("o", "Omar"), debtor("p", "Paula"), debtor("n", "Nadim"))
        val debts = listOf(debt("d1", "o", usd(5), "2026-10-01"), debt("d2", "p", usd(5), "2026-10-01"))
        val overview = overview(snapshot(debtors, debts, listOf(payment("p1", "d2", usd(5), "2026-10-02"))))

        assertEquals(listOf("Omar"), overview.names(DebtorFilter.OPEN))
        assertEquals(listOf("Paula"), overview.names(DebtorFilter.PAID))
        // Nadim has no debt yet: only under All.
        assertEquals(listOf("Omar", "Nadim", "Paula"), overview.names(DebtorFilter.ALL))
    }

    @Test
    fun searchIgnoresCaseSpacesAndArabicSpellingVariants() {
        val debtors = listOf(debtor("1", "أحمد خليل"), debtor("2", "هدى"), debtor("3", "Rami Haddad"), debtor("4", "مريم"))
        val overview = overview(snapshot(debtors))

        assertEquals(listOf("أحمد خليل"), overview.names(DebtorFilter.ALL, "احمد"))
        assertEquals(listOf("هدى"), overview.names(DebtorFilter.ALL, "هدي"))
        assertEquals(listOf("Rami Haddad"), overview.names(DebtorFilter.ALL, "  rami   HAD "))
        assertEquals(listOf("مريم"), overview.names(DebtorFilter.ALL, "مَرْيَم"))
        assertEquals(4, overview.names(DebtorFilter.ALL, "   ").size)
        assertEquals(emptyList<String>(), overview.names(DebtorFilter.ALL, "سامر"))
    }

    @Test
    fun searchAndFilterDoNotChangeTheGrandTotal() {
        val debts = listOf(debt("d1", "r", usd(100), "2026-10-01"), debt("d2", "h", usd(25), "2026-10-09"))
        val overview = overview(snapshot(listOf(rami, debtor("h", "هبة")), debts))

        assertEquals(listOf("هبة"), overview.names(DebtorFilter.OPEN, "هبة"))
        assertEquals(MoneyTotals.of(usd(125)), overview.totalRemaining)
    }

    @Test
    fun aPersonsPageShowsOpenDebtsOldestFirstThenPaidOnes() {
        val debts = listOf(
            debt("old-paid", "r", usd(10), "2026-08-01"),
            debt("recent", "r", usd(10), "2026-10-15"),
            debt("older", "r", lbp(50_000), "2026-09-01"),
            debt("new-paid", "r", usd(10), "2026-10-01"),
        )
        val payments = listOf(
            payment("p1", "old-paid", usd(10), "2026-08-02"),
            payment("p2", "new-paid", usd(10), "2026-10-02"),
        )
        val overview = overview(snapshot(listOf(rami), debts, payments))

        assertEquals(
            listOf("older", "recent", "new-paid", "old-paid"),
            overview.cards(overview.row("r")!!).map { it.account.debt.id },
        )
    }

    @Test
    fun anythingNotYetSyncedMarksTheRow() {
        val debtors = listOf(debtor("a", "أمل"), debtor("b", "باسم"), debtor("c", "كريم", pendingSync = true))
        val debts = listOf(debt("d1", "a", usd(10), "2026-10-01"), debt("d2", "b", usd(10), "2026-10-01", pendingSync = true))
        val overview = overview(snapshot(debtors, debts, listOf(payment("p1", "d1", usd(5), "2026-10-02", pendingSync = true))))

        assertTrue(overview.row("a")!!.pendingSync)
        assertTrue(overview.row("b")!!.pendingSync)
        assertTrue(overview.row("c")!!.pendingSync)
        assertTrue(overview.cards(overview.row("a")!!).single().pendingSync)
        assertEquals(3, overview.pendingCount)
    }

    @Test
    fun aMissingPersonOrDebtIsNull() {
        val overview = overview(snapshot(listOf(rami), listOf(loan)))

        assertNull(overview.row("gone"))
        assertNull(overview.account("gone"))
        assertEquals(usd(100), overview.account("d1")!!.remaining)
        assertFalse(overview.row("r")!!.balance.isSettled)
    }
}
