package com.shopfinance.app.ui.fakes

import com.shopfinance.app.data.debts.DebtRecord
import com.shopfinance.app.data.debts.DebtRejection
import com.shopfinance.app.data.debts.DebtRepository
import com.shopfinance.app.data.debts.DebtorRecord
import com.shopfinance.app.data.debts.DebtsSnapshot
import com.shopfinance.app.data.debts.NewDebt
import com.shopfinance.app.data.debts.NewDebtor
import com.shopfinance.app.data.debts.NewPayment
import com.shopfinance.app.data.debts.PaymentRecord
import com.shopfinance.domain.debts.Debt
import com.shopfinance.domain.debts.DebtAccount
import com.shopfinance.domain.debts.DebtPayment
import com.shopfinance.domain.money.Money
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * In-memory [DebtRepository] with one stream per shop. Tests send snapshots with [push], make
 * the stream fail with [fail] and report server refusals with [reject]. The latest event is
 * replayed to new listeners, like a snapshot listener that answers from the cache.
 *
 * Writes are recorded and, once a snapshot of the shop was pushed, applied to it as pending
 * changes, the way Firestore shows local writes at once. Like the real repository,
 * [addPayment] refuses a payment that [DebtAccount.checkPayment] refuses on [today].
 */
class FakeDebtRepository(private val today: LocalDate = LocalDate.of(2026, 10, 22)) : DebtRepository {

    data class DebtorCall(val shopId: String, val createdBy: String, val debtor: NewDebtor)

    data class DebtCall(val shopId: String, val createdBy: String, val debt: NewDebt)

    data class PaymentCall(val shopId: String, val createdBy: String, val debtId: String, val payment: NewPayment)

    data class DeleteCall(val shopId: String, val documentId: String, val deletedBy: String)

    private val streams = mutableMapOf<String, MutableSharedFlow<Event>>()
    private val latest = mutableMapOf<String, DebtsSnapshot>()
    private val rejectionEvents = MutableSharedFlow<DebtRejection>(extraBufferCapacity = 16)
    private val now: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC)

    /** The shop ids passed to [observeDebts], once per started listener. */
    val observeCalls = mutableListOf<String>()

    val debtorCalls = mutableListOf<DebtorCall>()
    val debtCalls = mutableListOf<DebtCall>()
    val paymentCalls = mutableListOf<PaymentCall>()
    val deleteDebtCalls = mutableListOf<DeleteCall>()
    val deletePaymentCalls = mutableListOf<DeleteCall>()

    /** When set, the add methods throw it instead of recording anything. */
    var addError: RuntimeException? = null

    fun push(shopId: String, snapshot: DebtsSnapshot) {
        latest[shopId] = snapshot
        check(stream(shopId).tryEmit(Event.Snapshot(snapshot)))
    }

    fun fail(shopId: String, error: Throwable) {
        check(stream(shopId).tryEmit(Event.Failure(error)))
    }

    fun reject(rejection: DebtRejection) {
        check(rejectionEvents.tryEmit(rejection))
    }

    override val rejections: Flow<DebtRejection> = rejectionEvents.asSharedFlow()

    override fun observeDebts(shopId: String): Flow<DebtsSnapshot> = flow {
        observeCalls += shopId
        stream(shopId).collect { event ->
            when (event) {
                is Event.Snapshot -> emit(event.snapshot)
                is Event.Failure -> throw event.error
            }
        }
    }

    override fun addDebtor(shopId: String, createdBy: String, debtor: NewDebtor): String {
        addError?.let { throw it }
        debtorCalls += DebtorCall(shopId, createdBy, debtor)
        val id = "debtor-${debtorCalls.size}"
        val record = DebtorRecord(id, debtor.name.trim(), debtor.phone.trim().ifEmpty { null }, debtor.note, now, pendingSync = true)
        apply(shopId) { it.copy(debtors = (it.debtors + record).sortedBy { debtorRecord -> debtorRecord.name }) }
        return id
    }

    override fun addDebt(shopId: String, createdBy: String, debt: NewDebt): String {
        addError?.let { throw it }
        debtCalls += DebtCall(shopId, createdBy, debt)
        val id = "debt-${debtCalls.size}"
        val record = DebtRecord(Debt(id, debt.debtorId, debt.amount, debt.debtDate, debt.note), now, pendingSync = true)
        apply(shopId) { it.copy(debts = listOf(record) + it.debts) }
        return id
    }

    override fun addPayment(shopId: String, createdBy: String, account: DebtAccount, payment: NewPayment): String {
        addError?.let { throw it }
        require(account.checkPayment(payment.amount, payment.paymentDate, today) == null) { "payment refused" }
        paymentCalls += PaymentCall(shopId, createdBy, account.debt.id, payment)
        val id = "payment-${paymentCalls.size}"
        val record = PaymentRecord(
            DebtPayment(id, account.debt.id, payment.amount, payment.paymentDate, payment.note),
            now,
            pendingSync = true,
        )
        apply(shopId) { it.copy(payments = listOf(record) + it.payments) }
        return id
    }

    override fun deleteDebt(shopId: String, debtId: String, deletedBy: String) {
        deleteDebtCalls += DeleteCall(shopId, debtId, deletedBy)
        apply(shopId) { snapshot ->
            snapshot.copy(
                debts = snapshot.debts.filter { it.debt.id != debtId },
                payments = snapshot.payments.filter { it.payment.debtId != debtId },
            )
        }
    }

    override fun deletePayment(shopId: String, paymentId: String, deletedBy: String) {
        deletePaymentCalls += DeleteCall(shopId, paymentId, deletedBy)
        apply(shopId) { snapshot -> snapshot.copy(payments = snapshot.payments.filter { it.payment.id != paymentId }) }
    }

    private fun apply(shopId: String, change: (DebtsSnapshot) -> DebtsSnapshot) {
        val snapshot = latest[shopId] ?: return
        push(shopId, change(snapshot))
    }

    private fun stream(shopId: String): MutableSharedFlow<Event> = streams.getOrPut(shopId) {
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private sealed interface Event {
        data class Snapshot(val snapshot: DebtsSnapshot) : Event
        data class Failure(val error: Throwable) : Event
    }

    companion object {
        private val CREATED_AT: Instant = Instant.parse("2026-10-01T09:00:00Z")

        /** A stored, synced person. */
        fun debtor(id: String, name: String, pendingSync: Boolean = false): DebtorRecord =
            DebtorRecord(id, name, phone = null, note = "", createdAt = CREATED_AT, pendingSync = pendingSync)

        /** A stored, synced debt of [amount] taken on [day] (`YYYY-MM-DD`). */
        fun debt(id: String, debtorId: String, amount: Money, day: String, pendingSync: Boolean = false): DebtRecord =
            DebtRecord(Debt(id, debtorId, amount, LocalDate.parse(day)), CREATED_AT, pendingSync)

        /** A stored, synced payment of [amount] on [day] (`YYYY-MM-DD`). */
        fun payment(id: String, debtId: String, amount: Money, day: String, pendingSync: Boolean = false): PaymentRecord =
            PaymentRecord(DebtPayment(id, debtId, amount, LocalDate.parse(day)), CREATED_AT, pendingSync)

        fun snapshot(
            debtors: List<DebtorRecord>,
            debts: List<DebtRecord> = emptyList(),
            payments: List<PaymentRecord> = emptyList(),
        ): DebtsSnapshot = DebtsSnapshot(debtors, debts, payments, fromCache = false)
    }
}
