package com.shopfinance.app.data.debts

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.shopfinance.app.data.shop.ShopAccessException
import com.shopfinance.app.data.shop.ShopFields
import com.shopfinance.domain.debts.DebtAccount
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.time.Instant
import java.time.LocalDate

/**
 * [DebtRepository] backed by Cloud Firestore, under shops/{shopId}/debtors, debts and
 * debtPayments. Like the ledger, writes go to the on-device cache first and are not awaited,
 * so they work offline; [rejections] reports the rare write the server later refuses.
 */
class FirestoreDebtRepository(
    private val firestore: FirebaseFirestore,
    private val today: () -> LocalDate = LocalDate::now,
) : DebtRepository {

    private val rejectionEvents = MutableSharedFlow<DebtRejection>(extraBufferCapacity = 16)
    override val rejections: Flow<DebtRejection> = rejectionEvents.asSharedFlow()

    override fun observeDebts(shopId: String): Flow<DebtsSnapshot> = callbackFlow {
        // The three listeners call back on the main thread, one at a time, so these need no locking.
        var debtors: List<DebtorRecord>? = null
        var debts: List<DebtRecord>? = null
        var payments: List<StoredPayment>? = null
        val fromCache = BooleanArray(3)

        fun publish() {
            val readyDebtors = debtors ?: return
            val readyDebts = debts ?: return
            val readyPayments = payments ?: return
            trySend(buildDebtsSnapshot(readyDebtors, readyDebts, readyPayments, fromCache.any { it }))
        }

        // INCLUDE: also hear when pending writes reach the server, so "not synced yet"
        // markers clear, and when a cached result is confirmed by the server.
        fun <T> listen(collection: String, index: Int, parse: (String, Map<String, Any?>, Boolean) -> T?, keep: (List<T>) -> Unit) =
            collection(shopId, collection).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(toReadFailure(error))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                keep(parseAll(snapshot, collection, parse))
                fromCache[index] = snapshot.metadata.isFromCache
                publish()
            }

        val registrations = listOf(
            listen(DebtFields.DEBTORS, 0, ::parseDebtor) { debtors = it },
            listen(DebtFields.DEBTS, 1, ::parseDebt) { debts = it },
            listen(DebtFields.PAYMENTS, 2, ::parsePayment) { payments = it },
        )
        awaitClose { registrations.forEach { it.remove() } }
    }.conflate()

    override fun addDebtor(shopId: String, createdBy: String, debtor: NewDebtor): String {
        requireIds(shopId, createdBy)
        val error = DebtInputRules.validateDebtor(debtor)
        require(error == null) { "Invalid debtor: $error" }
        return add(shopId, DebtFields.DEBTORS, DebtRejection.Kind.ADD_DEBTOR) {
            newDebtorFields(createdBy, debtor, FieldValue.serverTimestamp())
        }
    }

    override fun addDebt(shopId: String, createdBy: String, debt: NewDebt): String {
        requireIds(shopId, createdBy)
        require(debt.debtorId.isNotBlank()) { "debtorId must not be blank" }
        val error = DebtInputRules.validateDebt(debt, today())
        require(error == null) { "Invalid debt: $error" }
        return add(shopId, DebtFields.DEBTS, DebtRejection.Kind.ADD_DEBT) {
            newDebtFields(createdBy, debt, FieldValue.serverTimestamp())
        }
    }

    override fun addPayment(shopId: String, createdBy: String, account: DebtAccount, payment: NewPayment): String {
        requireIds(shopId, createdBy)
        val problem = account.checkPayment(payment.amount, payment.paymentDate, today())
        require(problem == null) { "Invalid payment: $problem" }
        require(!DebtInputRules.noteTooLong(payment.note)) { "Invalid payment: note too long" }
        return add(shopId, DebtFields.PAYMENTS, DebtRejection.Kind.ADD_PAYMENT) {
            newPaymentFields(createdBy, account.debt.id, payment, FieldValue.serverTimestamp())
        }
    }

    override fun deleteDebt(shopId: String, debtId: String, deletedBy: String) {
        markDeleted(shopId, DebtFields.DEBTS, debtId, deletedBy, DebtRejection.Kind.DELETE_DEBT)
    }

    override fun deletePayment(shopId: String, paymentId: String, deletedBy: String) {
        markDeleted(shopId, DebtFields.PAYMENTS, paymentId, deletedBy, DebtRejection.Kind.DELETE_PAYMENT)
    }

    /** Queues a new document in [collection] and returns its id at once. */
    private fun add(shopId: String, collection: String, kind: DebtRejection.Kind, fields: () -> Map<String, Any>): String {
        val document = collection(shopId, collection).document()
        // Not awaited, as in FirestoreLedgerRepository.addEntry: the Task only completes when
        // the server confirms, which may be much later when offline.
        document.set(fields())
            .addOnFailureListener { failure ->
                Log.w(TAG, "Writing $collection/${document.id} failed", failure)
                rejectionEvents.tryEmit(DebtRejection(kind, document.id, isPermissionDenied(failure)))
            }
        return document.id
    }

    private fun markDeleted(shopId: String, collection: String, id: String, deletedBy: String, kind: DebtRejection.Kind) {
        requireIds(shopId, deletedBy)
        require(id.isNotBlank()) { "id must not be blank" }
        collection(shopId, collection).document(id)
            .update(deleteFields(deletedBy, FieldValue.serverTimestamp()))
            .addOnFailureListener { failure ->
                Log.w(TAG, "Deleting $collection/$id failed", failure)
                rejectionEvents.tryEmit(DebtRejection(kind, id, isPermissionDenied(failure)))
            }
    }

    private fun requireIds(shopId: String, uid: String) {
        require(shopId.isNotBlank()) { "shopId must not be blank" }
        require(uid.isNotBlank()) { "user id must not be blank" }
    }

    private fun collection(shopId: String, name: String): CollectionReference =
        firestore.collection(ShopFields.COLLECTION).document(shopId).collection(name)

    private fun toReadFailure(error: FirebaseFirestoreException): Throwable =
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ShopAccessException(error) else error

    private fun isPermissionDenied(failure: Exception): Boolean =
        (failure as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private companion object {
        const val TAG = "FirestoreDebts"
    }
}

/** Every readable document of [snapshot]; unreadable ones are logged and skipped. */
private fun <T> parseAll(
    snapshot: QuerySnapshot,
    collection: String,
    parse: (String, Map<String, Any?>, Boolean) -> T?,
): List<T> = snapshot.documents.mapNotNull { document ->
    // ESTIMATE: a local write's server timestamps read as the device's time instead of
    // null until the server sets them.
    val data = document.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
    val parsed = parse(document.id, data.withInstants(), document.metadata.hasPendingWrites())
    if (parsed == null && DebtFields.DELETED_AT !in data) Log.w("FirestoreDebts", "Skipping unreadable $collection/${document.id}")
    parsed
}

/** The document's fields with every Firestore [Timestamp] turned into an [Instant]. */
private fun Map<String, Any?>.withInstants(): Map<String, Any?> = mapValues { (_, value) ->
    if (value is Timestamp) Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong()) else value
}
