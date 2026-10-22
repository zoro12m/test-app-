package com.shopfinance.app.data.ledger

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.shopfinance.app.data.shop.ShopAccessException
import com.shopfinance.app.data.shop.ShopFields
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.time.Instant

/**
 * [LedgerRepository] backed by Cloud Firestore, under shops/{shopId}/entries. Like the shop
 * repository, writes go to the on-device cache first and are not awaited, so recording a
 * sale works offline; [rejections] reports the rare write the server later refuses.
 */
class FirestoreLedgerRepository(
    private val firestore: FirebaseFirestore,
    private val clock: () -> Instant = Instant::now,
) : LedgerRepository {

    private val rejectionEvents = MutableSharedFlow<LedgerRejection>(extraBufferCapacity = 16)
    override val rejections: Flow<LedgerRejection> = rejectionEvents.asSharedFlow()

    override fun observeEntries(shopId: String): Flow<LedgerSnapshot> = callbackFlow {
        // INCLUDE: also hear when pending writes reach the server, so "not synced yet"
        // markers clear, and when a cached result is confirmed by the server.
        val registration = entries(shopId)
            .orderBy(LedgerFields.OCCURRED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(toReadFailure(error))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val records = snapshot.documents.mapNotNull { document ->
                    // ESTIMATE: a local write's server timestamps read as the device's time
                    // instead of null until the server sets them.
                    val data = document.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: return@mapNotNull null
                    val record = parseEntry(document.id, data.withInstants(), document.metadata.hasPendingWrites())
                    if (record == null) Log.w(TAG, "Skipping unreadable entry ${document.id}")
                    record
                }
                trySend(LedgerSnapshot(sortRecords(records), fromCache = snapshot.metadata.isFromCache))
            }
        awaitClose { registration.remove() }
    }.conflate()

    override fun addEntry(shopId: String, createdBy: String, entry: NewEntry): String {
        require(shopId.isNotBlank()) { "shopId must not be blank" }
        require(createdBy.isNotBlank()) { "createdBy must not be blank" }
        val error = LedgerInputRules.validate(entry, clock())
        require(error == null) { "Invalid entry: $error" }

        val document = entries(shopId).document()
        // Not awaited, as in FirestoreShopRepository.createShop: the Task only completes
        // when the server confirms, which may be much later when offline.
        document.set(newEntryFields(createdBy, entry, FieldValue.serverTimestamp()))
            .addOnFailureListener { failure ->
                Log.w(TAG, "Recording entry ${document.id} failed", failure)
                rejectionEvents.tryEmit(
                    LedgerRejection(LedgerRejection.Kind.ADD, document.id, isPermissionDenied(failure)),
                )
            }
        return document.id
    }

    override fun voidEntry(shopId: String, entryId: String, voidedBy: String, reason: String) {
        require(shopId.isNotBlank()) { "shopId must not be blank" }
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(voidedBy.isNotBlank()) { "voidedBy must not be blank" }
        val error = LedgerInputRules.validateVoidReason(reason)
        require(error == null) { "Invalid void reason: $error" }

        entries(shopId).document(entryId)
            .update(voidFields(voidedBy, reason, FieldValue.serverTimestamp()))
            .addOnFailureListener { failure ->
                Log.w(TAG, "Voiding entry $entryId failed", failure)
                rejectionEvents.tryEmit(
                    LedgerRejection(LedgerRejection.Kind.VOID, entryId, isPermissionDenied(failure)),
                )
            }
    }

    private fun entries(shopId: String): CollectionReference =
        firestore.collection(ShopFields.COLLECTION).document(shopId).collection(LedgerFields.COLLECTION)

    private fun toReadFailure(error: FirebaseFirestoreException): Throwable =
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ShopAccessException(error) else error

    private fun isPermissionDenied(failure: Exception): Boolean =
        (failure as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private companion object {
        const val TAG = "FirestoreLedger"
    }
}

/** The document's fields with every Firestore [Timestamp] turned into an [Instant], for [parseEntry]. */
private fun Map<String, Any?>.withInstants(): Map<String, Any?> = mapValues { (_, value) ->
    if (value is Timestamp) Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong()) else value
}
