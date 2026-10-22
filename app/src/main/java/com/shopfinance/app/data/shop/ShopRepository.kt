package com.shopfinance.app.data.shop

import com.shopfinance.domain.money.Currency
import kotlinx.coroutines.flow.Flow

/** A shop the signed-in user belongs to. */
data class Shop(
    val id: String,
    val name: String,
    val ownerUid: String,
    val currency: Currency,
)

/**
 * The shops a user belongs to, and whether that list came only from the device cache.
 * An empty list from the cache does not mean the user has no shop: the server may not
 * have answered yet.
 */
data class ShopsSnapshot(
    val shops: List<Shop>,
    val fromCache: Boolean,
)

interface ShopRepository {
    /**
     * Live list of shops whose members include [uid], sorted by name. The flow fails with
     * [ShopAccessException] when Firestore refuses the read (for example when the security
     * rules are not deployed) and with other exceptions for anything else.
     */
    fun observeShops(uid: String): Flow<ShopsSnapshot>

    /**
     * Creates a shop owned by [ownerUid] and returns its new id at once. The write is queued
     * locally and reaches the server when online, so this works offline; the new shop shows
     * up in [observeShops] immediately.
     *
     * If the server later refuses the write, Firestore removes the local copy and
     * [onRejected] is called on the main thread, so the caller can let the person try again.
     */
    fun createShop(ownerUid: String, name: String, onRejected: (Throwable) -> Unit = {}): String
}

/** Firestore refused access (PERMISSION_DENIED). */
class ShopAccessException(cause: Throwable? = null) : Exception("Access to shop data was denied", cause)
