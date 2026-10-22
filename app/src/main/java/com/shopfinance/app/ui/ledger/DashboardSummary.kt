package com.shopfinance.app.ui.ledger

import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.ledger.Ledger
import com.shopfinance.domain.ledger.LedgerEntry
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
import java.time.Instant
import java.time.ZoneId

/**
 * The numbers on the dashboard, all in USD and all derived from the ledger (voided entries
 * count for nothing).
 *
 * @property cashOnHand everything recorded so far: sales and owner deposits in, expenses and
 *   owner withdrawals out. Negative when more went out than was recorded coming in.
 * @property monthNet this month's sales minus this month's expenses. Owner deposits and
 *   withdrawals are left out: they are not the shop earning or spending.
 */
data class DashboardSummary(
    val cashOnHand: Money,
    val todaySales: Money,
    val todayExpenses: Money,
    val monthSales: Money,
    val monthExpenses: Money,
) {
    val monthNet: Money get() = monthSales - monthExpenses

    companion object {
        val EMPTY = DashboardSummary(
            cashOnHand = Money.zero(Currency.USD),
            todaySales = Money.zero(Currency.USD),
            todayExpenses = Money.zero(Currency.USD),
            monthSales = Money.zero(Currency.USD),
            monthExpenses = Money.zero(Currency.USD),
        )

        /**
         * Builds the summary at [now]. "Today" and "this month" follow the calendar in [zone],
         * the phone's time zone, so a sale at 23:30 in Beirut counts for that day.
         */
        fun of(entries: List<LedgerEntry>, now: Instant, zone: ZoneId): DashboardSummary {
            val ledger = Ledger(entries.distinctBy { it.id })
            val today = now.atZone(zone).toLocalDate()
            val dayStart = today.atStartOfDay(zone).toInstant()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
            val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
            val monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant()

            val todayTotals = ledger.totalsByType(dayStart, dayEnd)
            val monthTotals = ledger.totalsByType(monthStart, monthEnd)
            return DashboardSummary(
                cashOnHand = ledger.balances().cashOnHand.usd(),
                todaySales = todayTotals[EntryType.SALE].usd(),
                todayExpenses = todayTotals[EntryType.EXPENSE].usd(),
                monthSales = monthTotals[EntryType.SALE].usd(),
                monthExpenses = monthTotals[EntryType.EXPENSE].usd(),
            )
        }

        private fun MoneyTotals?.usd(): Money = this?.get(Currency.USD) ?: Money.zero(Currency.USD)
    }
}
