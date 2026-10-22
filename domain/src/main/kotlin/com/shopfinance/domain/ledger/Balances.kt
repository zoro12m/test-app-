package com.shopfinance.domain.ledger

import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals

/**
 * The shop's position, per currency.
 *
 * @property cashOnHand money in the shop.
 * @property receivables what customers owe the shop.
 * @property payables what the shop owes suppliers and lenders.
 * @property capital what the owner has put in, net of withdrawals.
 */
data class Balances(
    val cashOnHand: MoneyTotals,
    val receivables: MoneyTotals,
    val payables: MoneyTotals,
    val capital: MoneyTotals,
) {
    /** Cash plus what the shop is owed, minus what it owes. */
    val netPosition: MoneyTotals get() = cashOnHand + receivables - payables

    companion object {
        val EMPTY = Balances(MoneyTotals.EMPTY, MoneyTotals.EMPTY, MoneyTotals.EMPTY, MoneyTotals.EMPTY)
    }
}

/** Money in and out over a period, per currency. Both totals are positive. */
data class CashFlow(val inflow: MoneyTotals, val outflow: MoneyTotals) {
    val net: MoneyTotals get() = inflow - outflow
}

/** A cached party balance that no longer matches the ledger. */
data class BalanceDrift(
    val partyId: String,
    val cached: Money,
    val actual: Money,
) {
    /** How far the cache is off: positive means the cache is too high. */
    val difference: Money get() = cached - actual
}
