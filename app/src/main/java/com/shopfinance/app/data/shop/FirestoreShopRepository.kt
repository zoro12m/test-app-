package com.shopfinance.app.data.shop

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * [ShopRepository] backed by Cloud Firestore. Reads and writes go through Firestore's
 * on-device cache, so the app keeps working offline and syncs when the connection is back.
 */
class FirestoreShopRepository(
    private val firestore: FirebaseFirestore,
) : ShopRepository {

    override fun observeShops(uid: String): Flow<ShopsSnapshot> = callbackFlow {
        // INCLUDE: also hear when a cached result is confirmed by the server, so an empty
        // list from the cache is followed by the server's answer even if nothing changed.
        val registration = firestore.collection(ShopFields.COLLECTION)
            .whereArrayContains(ShopFields.MEMBER_UIDS, uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(toShopsFailure(error))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val shops = snapshot.documents.mapNotNull { document ->
                    document.data?.let { data -> parseShop(document.id, data) }
                }
                trySend(
                    ShopsSnapshot(
                        shops = sortShops(shops),
                        fromCache = snapshot.metadata.isFromCache,
                    ),
                )
            }
        awaitClose { registration.remove() }
    }.conflate() // Only the latest snapshot matters.

    override fun createShop(ownerUid: String, name: String, onRejected: (Throwable) -> Unit): String {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        val nameError = ShopNameRules.validate(name)
        require(nameError == null) { "Invalid shop name: $nameError" }

        val document = firestore.collection(ShopFields.COLLECTION).document()
        val fields = newShopFields(
            ownerUid = ownerUid,
            name = ShopNameRules.normalize(name),
            serverTimestamp = FieldValue.serverTimestamp(),
        )
        // Not awaited: the write lands in the local cache at once (observeShops sees it right
        // away) and the Task only completes when the server confirms, which may be much later
        // when offline. On a refusal Firestore removes the local copy; we log it and tell the caller.
        document.set(fields)
            .addOnFailureListener { error ->
                Log.w(TAG, "Creating shop ${document.id} failed", error)
                onRejected(error)
            }
        return document.id
    }

    private fun toShopsFailure(error: FirebaseFirestoreException): Throwable =
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            ShopAccessException(error)
        } else {
            error
        }

    private companion object {
        const val TAG = "FirestoreShops"
    }
}
