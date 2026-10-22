package com.shopfinance.domain.debts

import com.shopfinance.domain.money.MoneyTotals
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Everything one person owes, worked out from their debts.
 *
 * @property accounts the person's debts: open ones first, oldest debt date first, then paid
 *   ones, newest debt date first.
 */
class DebtorBalance(val debtorId: String, accounts: List<DebtAccount>) {

    val accounts: List<DebtAccount> = accounts.filter { it.isOpen }.sortedBy { it.debt.debtDate } +
        accounts.filterNot { it.isOpen }.sortedByDescending { it.debt.debtDate }

    init {
        for (account in this.accounts) {
            require(account.debt.debtorId == debtorId) { "Debt ${account.debt.id} belongs to ${account.debt.debtorId}" }
        }
    }

    /** What is still owed, one total per currency. USD and LBP are never added together. */
    val remaining: MoneyTotals = MoneyTotals.sum(this.accounts.filter { it.isOpen }.map { it.remaining })

    /** The earliest debt date among the open debts, or null when nothing is owed. */
    val oldestUnpaid: LocalDate? = this.accounts.firstOrNull { it.isOpen }?.debt?.debtDate

    val hasDebts: Boolean get() = accounts.isNotEmpty()

    /** True when the person has debts and every one of them is paid. */
    val isSettled: Boolean get() = hasDebts && oldestUnpaid == null

    /** Whole days from [oldestUnpaid] to [today], or null when nothing is owed. Never negative. */
    fun daysSinceOldestUnpaid(today: LocalDate): Long? =
        oldestUnpaid?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }

    override fun equals(other: Any?): Boolean =
        other is DebtorBalance && other.debtorId == debtorId && other.accounts == accounts

    override fun hashCode(): Int = 31 * debtorId.hashCode() + accounts.hashCode()

    override fun toString(): String = "DebtorBalance($debtorId, remaining=$remaining)"
}

object DebtBook {

    /**
     * Pairs each debt with its payments. Payments whose debt is not in [debts] (for example a
     * deleted one) are left out.
     */
    fun accounts(debts: Iterable<Debt>, payments: Iterable<DebtPayment>): List<DebtAccount> {
        val byDebt = payments.groupBy { it.debtId }
        return debts.map { DebtAccount(it, byDebt[it.id].orEmpty()) }
    }

    /** One balance per id in [debtorIds], including people with no debts yet. */
    fun balances(debtorIds: Iterable<String>, accounts: Iterable<DebtAccount>): Map<String, DebtorBalance> {
        val byDebtor = accounts.groupBy { it.debt.debtorId }
        return debtorIds.associateWith { DebtorBalance(it, byDebtor[it].orEmpty()) }
    }

    /** What everyone still owes, one total per currency. */
    fun totalRemaining(balances: Iterable<DebtorBalance>): MoneyTotals =
        balances.fold(MoneyTotals.EMPTY) { sum, balance -> sum + balance.remaining }
}
