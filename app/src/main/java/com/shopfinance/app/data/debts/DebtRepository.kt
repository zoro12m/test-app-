package com.shopfinance.app.data.debts

import com.shopfinance.domain.debts.Debt
import com.shopfinance.domain.debts.DebtAccount
import com.shopfinance.domain.debts.DebtBook
import com.shopfinance.domain.debts.DebtPayment
import com.shopfinance.domain.money.Money
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** A person who owes the shop money, as the app shows them. */
data class DebtorRecord(
    val id: String,
    val name: String,
    /** Null when no phone was given. */
    val phone: String?,
    val note: String,
    val createdAt: Instant,
    val pendingSync: Boolean,
)

/** A debt plus when it was typed in and whether the device still has to send it. */
data class DebtRecord(
    val debt: Debt,
    val createdAt: Instant,
    val pendingSync: Boolean,
)

/** A payment plus when it was typed in and whether the device still has to send it. */
data class PaymentRecord(
    val payment: DebtPayment,
    val createdAt: Instant,
    val pendingSync: Boolean,
)

/**
 * Every debtor, debt and payment of a shop that is not deleted. Payments of a deleted debt
 * are left out too. [fromCache] is true while any part came only from the device cache.
 *
 * @property debtors by name.
 * @property payments the most recently typed in first.
 */
data class DebtsSnapshot(
    val debtors: List<DebtorRecord>,
    val debts: List<DebtRecord>,
    val payments: List<PaymentRecord>,
    val fromCache: Boolean,
) {
    /** Each debt with its payments. Remaining amounts are worked out here, never read from storage. */
    fun accounts(): List<DebtAccount> = DebtBook.accounts(debts.map { it.debt }, payments.map { it.payment })

    val pendingCount: Int
        get() = debtors.count { it.pendingSync } + debts.count { it.pendingSync } + payments.count { it.pendingSync }

    companion object {
        val EMPTY = DebtsSnapshot(emptyList(), emptyList(), emptyList(), fromCache = false)
    }
}

/** A debtor typed by the person, before it is stored. An empty [phone] means none. */
data class NewDebtor(val name: String, val phone: String, val note: String)

/** A debt typed by the person, before it is stored. See [DebtInputRules.validateDebt]. */
data class NewDebt(val debtorId: String, val amount: Money, val debtDate: LocalDate, val note: String)

/** A payment typed by the person, in the currency of the debt it pays. */
data class NewPayment(val amount: Money, val paymentDate: LocalDate, val note: String)

/** The server refused a write that the device had already applied locally. */
data class DebtRejection(
    val kind: Kind,
    val documentId: String,
    /** Firestore said PERMISSION_DENIED, usually because the security rules are not published. */
    val accessDenied: Boolean,
) {
    enum class Kind { ADD_DEBTOR, ADD_DEBT, ADD_PAYMENT, DELETE_DEBT, DELETE_PAYMENT }
}

interface DebtRepository {
    /**
     * Live debtors, debts and payments of the shop [shopId]. The flow fails with
     * [com.shopfinance.app.data.shop.ShopAccessException] when Firestore refuses the read.
     */
    fun observeDebts(shopId: String): Flow<DebtsSnapshot>

    /**
     * Stores [debtor] and returns its new id at once. Like the ledger, every write here is
     * queued locally, so it works offline and shows up in [observeDebts] immediately.
     *
     * @throws IllegalArgumentException when [debtor] fails [DebtInputRules.validateDebtor].
     */
    fun addDebtor(shopId: String, createdBy: String, debtor: NewDebtor): String

    /** @throws IllegalArgumentException when [debt] fails [DebtInputRules.validateDebt]. */
    fun addDebt(shopId: String, createdBy: String, debt: NewDebt): String

    /**
     * Records [payment] against [account], the debt as the device sees it now.
     *
     * @throws IllegalArgumentException when [DebtAccount.checkPayment] refuses the payment
     *   (for example more than the remaining) or its note is too long. Nothing is written then.
     */
    fun addPayment(shopId: String, createdBy: String, account: DebtAccount, payment: NewPayment): String

    /** Marks the debt [debtId] deleted, which hides it and its payments. The document stays. */
    fun deleteDebt(shopId: String, debtId: String, deletedBy: String)

    /** Marks the payment [paymentId] deleted, which hides it. The document stays. */
    fun deletePayment(shopId: String, paymentId: String, deletedBy: String)

    /** Writes the server refused after they were applied locally. Firestore has undone them on the device. */
    val rejections: Flow<DebtRejection>
}
