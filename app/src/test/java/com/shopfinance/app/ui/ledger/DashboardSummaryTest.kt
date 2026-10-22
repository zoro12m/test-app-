package com.shopfinance.app.ui.ledger

import com.shopfinance.app.ui.fakes.FakeLedgerRepository.Companion.record
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.ledger.LedgerEntry
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DashboardSummaryTest {

    private val beirut = ZoneId.of("Asia/Beirut")

    /** 22 October 2026, 14:00 in Beirut (UTC+3). */
    private val now = Instant.parse("2026-10-22T11:00:00Z")

    private fun usd(cents: Long) = Money(cents, Currency.USD)

    private fun entry(id: String, type: EntryType, cents: Long, at: String, categoryId: String? = null): LedgerEntry =
        record(id, type, cents, Instant.parse(at), categoryId).entry

    private fun summary(vararg entries: LedgerEntry) = DashboardSummary.of(entries.toList(), now, beirut)

    @Test
    fun noEntriesMeansZeroEverywhere() {
        assertEquals(DashboardSummary.EMPTY, summary())
    }

    @Test
    fun cashOnHandCountsEveryCashMovementEver() {
        val result = summary(
            entry("1", EntryType.CAPITAL_IN, 50_000, "2026-01-05T08:00:00Z"),
            entry("2", EntryType.SALE, 12_000, "2026-09-10T08:00:00Z"),
            entry("3", EntryType.EXPENSE, 4_000, "2026-10-22T07:00:00Z", "rent"),
            entry("4", EntryType.CAPITAL_OUT, 10_000, "2026-10-21T07:00:00Z"),
        )
        assertEquals(usd(50_000 + 12_000 - 4_000 - 10_000), result.cashOnHand)
    }

    @Test
    fun cashOnHandCanGoNegative() {
        assertEquals(usd(-3_000), summary(entry("1", EntryType.EXPENSE, 3_000, "2026-10-22T07:00:00Z", "rent")).cashOnHand)
    }

    @Test
    fun todayAndThisMonthFollowTheLocalCalendar() {
        val result = summary(
            // 21 Oct 23:30 in Beirut: yesterday.
            entry("1", EntryType.SALE, 100, "2026-10-21T20:30:00Z"),
            // 22 Oct 00:30 in Beirut, still 21 Oct in UTC: today.
            entry("2", EntryType.SALE, 200, "2026-10-21T21:30:00Z"),
            entry("3", EntryType.SALE, 400, "2026-10-22T10:00:00Z"),
            // 1 Oct 01:00 in Beirut, 30 Sep in UTC: this month.
            entry("4", EntryType.SALE, 800, "2026-09-30T22:00:00Z"),
            // 30 Sep 23:00 in Beirut: last month.
            entry("5", EntryType.SALE, 1_600, "2026-09-30T20:00:00Z"),
        )
        assertEquals(usd(200 + 400), result.todaySales)
        assertEquals(usd(100 + 200 + 400 + 800), result.monthSales)
    }

    @Test
    fun expensesAndTheMonthNet() {
        val result = summary(
            entry("1", EntryType.SALE, 10_000, "2026-10-22T08:00:00Z"),
            entry("2", EntryType.EXPENSE, 2_500, "2026-10-22T09:00:00Z", "goods"),
            entry("3", EntryType.EXPENSE, 4_000, "2026-10-03T09:00:00Z", "electricity"),
        )
        assertEquals(usd(2_500), result.todayExpenses)
        assertEquals(usd(6_500), result.monthExpenses)
        assertEquals(usd(10_000 - 6_500), result.monthNet)
    }

    @Test
    fun ownerDepositsAndWithdrawalsAreNotSalesOrExpenses() {
        val result = summary(
            entry("1", EntryType.CAPITAL_IN, 20_000, "2026-10-22T08:00:00Z"),
            entry("2", EntryType.CAPITAL_OUT, 5_000, "2026-10-22T09:00:00Z"),
        )
        assertEquals(usd(15_000), result.cashOnHand)
        assertEquals(usd(0), result.todaySales)
        assertEquals(usd(0), result.todayExpenses)
        assertEquals(usd(0), result.monthNet)
    }

    @Test
    fun voidedEntriesCountForNothing() {
        val sale = entry("1", EntryType.SALE, 9_900, "2026-10-22T08:00:00Z")
            .void(Instant.parse("2026-10-22T08:05:00Z"), "سُجّل مرتين")
        val result = summary(sale, entry("2", EntryType.SALE, 100, "2026-10-22T08:10:00Z"))
        assertEquals(usd(100), result.cashOnHand)
        assertEquals(usd(100), result.todaySales)
    }

    @Test
    fun aRepeatedEntryIsCountedOnce() {
        val sale = entry("1", EntryType.SALE, 700, "2026-10-22T08:00:00Z")
        assertEquals(usd(700), summary(sale, sale).cashOnHand)
    }
}
