package com.shopfinance.domain.ledger

import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
import java.time.Instant

/**
 * Calculations over a set of ledger entries. Balances are always derived from the entries;
 * voided entries count for nothing.
 *
 * Every function rejects a list holding the same entry id twice, since that means the same
 * money would be counted twice.
 */
class Ledger(entries: Iterable<LedgerEntry>) {

    val entries: List<LedgerEntry> = entries.toList()

    init {
        val seen = HashSet<String>()
        for (entry in this.entries) {
            require(seen.add(entry.id)) { "Duplicate ledger entry id: ${entry.id}" }
        }
    }

    /** Entries that count: not voided. */
    val active: List<LedgerEntry> get() = entries.filter { !it.isVoided }

    /** Balances from every active entry that occurred at or before [asOf] (all of them when null). */
    fun balances(asOf: Instant? = null): Balances =
        active
            .filter { asOf == null || !it.occurredAt.isAfter(asOf) }
            .fold(Balances.EMPTY) { acc, entry ->
                val type = entry.type
                Balances(
                    cashOnHand = acc.cashOnHand.add(entry.amount, type.cash),
                    receivables = acc.receivables.add(entry.amount, type.receivable),
                    payables = acc.payables.add(entry.amount, type.payable),
                    capital = acc.capital.add(entry.amount, type.capital),
                )
            }

    /** Cash in and out for entries with [from] <= occurredAt < [until]. */
    fun cashFlow(from: Instant, until: Instant): CashFlow {
        require(from.isBefore(until)) { "from must be before until" }
        var inflow = MoneyTotals.EMPTY
        var outflow = MoneyTotals.EMPTY
        for (entry in inPeriod(from, until)) {
            when (entry.type.cash) {
                1 -> inflow += entry.amount
                -1 -> outflow += entry.amount
            }
        }
        return CashFlow(inflow, outflow)
    }

    /** Totals per entry type for entries with [from] <= occurredAt < [until]. Types with no entries are left out. */
    fun totalsByType(from: Instant, until: Instant): Map<EntryType, MoneyTotals> =
        inPeriod(from, until)
            .groupBy { it.type }
            .mapValues { (_, list) -> MoneyTotals.sum(list.map { it.amount }) }

    /** Expense totals per category (null for uncategorised) for [from] <= occurredAt < [until]. */
    fun expensesByCategory(from: Instant, until: Instant): Map<String?, MoneyTotals> =
        inPeriod(from, until)
            .filter { it.type == EntryType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> MoneyTotals.sum(list.map { it.amount }) }

    /**
     * Each party's balance per currency. Positive means the party owes the shop (a customer
     * with unpaid credit); negative means the shop owes the party (a supplier or lender).
     * Parties whose balance is zero in every currency are left out.
     */
    fun partyBalances(): Map<String, MoneyTotals> {
        val result = LinkedHashMap<String, MoneyTotals>()
        for (entry in active) {
            val partyId = entry.partyId ?: continue
            val direction = entry.type.receivable - entry.type.payable
            if (direction == 0) continue
            result[partyId] = (result[partyId] ?: MoneyTotals.EMPTY).add(entry.amount, direction)
        }
        return result.filterValues { !it.isZero }
    }

    fun partyBalance(partyId: String): MoneyTotals = partyBalances()[partyId] ?: MoneyTotals.EMPTY

    /**
     * Compares cached party balances (as stored on party documents) with the ledger and
     * returns every (party, currency) pair that differs, including parties missing from
     * either side.
     */
    fun reconcile(cached: Map<String, MoneyTotals>): List<BalanceDrift> {
        val actual = partyBalances()
        return (cached.keys + actual.keys).sorted().flatMap { partyId ->
            val cachedTotals = cached[partyId] ?: MoneyTotals.EMPTY
            val actualTotals = actual[partyId] ?: MoneyTotals.EMPTY
            (cachedTotals.currencies + actualTotals.currencies).distinct().sorted().mapNotNull { currency ->
                val c = cachedTotals[currency]
                val a = actualTotals[currency]
                if (c == a) null else BalanceDrift(partyId, c, a)
            }
        }
    }

    /** Entries for one party, oldest first, voided ones included so a statement shows them. */
    fun statement(partyId: String): List<LedgerEntry> =
        entries.filter { it.partyId == partyId }.sortedWith(compareBy({ it.occurredAt }, { it.id }))

    private fun inPeriod(from: Instant, until: Instant): List<LedgerEntry> =
        active.filter { !it.occurredAt.isBefore(from) && it.occurredAt.isBefore(until) }

    private fun MoneyTotals.add(amount: Money, sign: Int): MoneyTotals = when (sign) {
        1 -> this + amount
        -1 -> this - amount
        else -> this
    }
}
