package com.shopfinance.app.ui.debts

import com.shopfinance.app.data.debts.DebtRecord
import com.shopfinance.app.data.debts.DebtorRecord
import com.shopfinance.app.data.debts.DebtsSnapshot
import com.shopfinance.app.data.debts.PaymentRecord
import com.shopfinance.domain.debts.DebtAccount
import com.shopfinance.domain.debts.DebtBook
import com.shopfinance.domain.debts.DebtorBalance
import com.shopfinance.domain.money.MoneyTotals
import java.time.LocalDate

/** Which people the debtors list shows. */
enum class DebtorFilter {
    /** People with at least one open debt. */
    OPEN,

    /** People who have debts, all of them paid. */
    PAID,

    /** Everyone, including people with no debt yet. */
    ALL,
}

/**
 * One person in the debtors list.
 *
 * @property daysSinceOldestUnpaid whole days since [DebtorBalance.oldestUnpaid], or null when
 *   nothing is owed.
 * @property pendingSync the person, or one of their debts or payments, has not reached the
 *   server yet.
 */
data class DebtorRow(
    val debtor: DebtorRecord,
    val balance: DebtorBalance,
    val daysSinceOldestUnpaid: Long?,
    val pendingSync: Boolean,
) {
    fun shows(filter: DebtorFilter): Boolean = when (filter) {
        DebtorFilter.OPEN -> balance.oldestUnpaid != null
        DebtorFilter.PAID -> balance.isSettled
        DebtorFilter.ALL -> true
    }
}

/**
 * A debt on a person's page.
 *
 * @property payments the debt's payments in [DebtAccount.payments] order: newest payment date first.
 */
data class DebtCard(
    val account: DebtAccount,
    val record: DebtRecord,
    val payments: List<PaymentRecord>,
) {
    val pendingSync: Boolean get() = record.pendingSync || payments.any { it.pendingSync }
}

/** Everything the debts screens show, worked out from one [DebtsSnapshot] on the shop's [today]. */
class DebtsOverview(snapshot: DebtsSnapshot, val today: LocalDate) {

    private val accounts: List<DebtAccount> = snapshot.accounts()
    private val debtRecords: Map<String, DebtRecord> = snapshot.debts.associateBy { it.debt.id }
    private val paymentRecords: Map<String, PaymentRecord> = snapshot.payments.associateBy { it.payment.id }
    private val balances: Map<String, DebtorBalance> = DebtBook.balances(snapshot.debtors.map { it.id }, accounts)

    /**
     * Every person: those owing money first, the oldest unpaid debt first, then everyone else
     * by name.
     */
    val rows: List<DebtorRow> = snapshot.debtors
        .map { debtor ->
            val balance = balances.getValue(debtor.id)
            DebtorRow(
                debtor = debtor,
                balance = balance,
                daysSinceOldestUnpaid = balance.daysSinceOldestUnpaid(today),
                pendingSync = debtor.pendingSync || balance.accounts.any { card(it).pendingSync },
            )
        }
        .sortedWith(
            compareBy<DebtorRow> { it.balance.oldestUnpaid == null }
                .thenBy { it.balance.oldestUnpaid }
                .thenBy { NameSearch.key(it.debtor.name) }
                .thenBy { it.debtor.id },
        )

    /** What everyone still owes, one total per currency, whatever the filter or search. */
    val totalRemaining: MoneyTotals = DebtBook.totalRemaining(balances.values)

    /** Changes the device has not sent to the server yet. */
    val pendingCount: Int = snapshot.pendingCount

    /** The rows that pass [filter] and whose name matches [query], in [rows] order. */
    fun rows(filter: DebtorFilter, query: String): List<DebtorRow> =
        rows.filter { it.shows(filter) && NameSearch.matches(it.debtor.name, query) }

    /** The person [debtorId], or null when they are not (or no longer) in the snapshot. */
    fun row(debtorId: String): DebtorRow? = rows.firstOrNull { it.debtor.id == debtorId }

    /** [row]'s debts as the person's page shows them: open first, oldest first, then paid, newest first. */
    fun cards(row: DebtorRow): List<DebtCard> = row.balance.accounts.map { card(it) }

    /** The live account of the debt [debtId], or null when it is gone (for example deleted). */
    fun account(debtId: String): DebtAccount? = accounts.firstOrNull { it.debt.id == debtId }

    private fun card(account: DebtAccount): DebtCard = DebtCard(
        account = account,
        record = debtRecords.getValue(account.debt.id),
        payments = account.payments.map { paymentRecords.getValue(it.id) },
    )
}
