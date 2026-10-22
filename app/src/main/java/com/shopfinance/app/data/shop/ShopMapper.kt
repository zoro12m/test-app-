package com.shopfinance.app.data.shop

import com.shopfinance.domain.money.Currency

/** Collection and field names of a shops/{shopId} document. They must match firestore.rules. */
internal object ShopFields {
    const val COLLECTION = "shops"
    const val NAME = "name"
    const val OWNER_UID = "ownerUid"
    const val MEMBER_UIDS = "memberUids"
    const val CURRENCY = "currency"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val SCHEMA_VERSION = "schemaVersion"
}

/** The schema version new shop documents are written with. */
internal const val SHOP_SCHEMA_VERSION = 1L

/**
 * Currencies a shop may use. USD only for now: the app never offers LBP, and the security
 * rules only accept "USD", so a document with any other currency is skipped.
 */
internal val SUPPORTED_SHOP_CURRENCIES: Set<Currency> = setOf(Currency.USD)

/**
 * Reads a shops/{shopId} document as Firestore returns it (strings as String, integers as
 * Long, arrays as List). Returns null, so the caller skips the document, when a required
 * field is missing or has the wrong type, or the currency is not exactly a supported code.
 * Timestamps and schemaVersion are not needed and are not read.
 */
fun parseShop(id: String, data: Map<String, Any?>): Shop? {
    if (id.isBlank()) return null
    val name = (data[ShopFields.NAME] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val ownerUid = (data[ShopFields.OWNER_UID] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val currencyCode = data[ShopFields.CURRENCY] as? String ?: return null
    val currency = SUPPORTED_SHOP_CURRENCIES.firstOrNull { it.code == currencyCode } ?: return null
    return Shop(id = id, name = name, ownerUid = ownerUid, currency = currency)
}

/**
 * The fields of a new shop document, exactly the keys firestore.rules accepts on create.
 * [name] must already be normalized with [ShopNameRules.normalize]. [serverTimestamp] is
 * Firestore's server timestamp sentinel; it is a parameter so this stays free of Firebase
 * types and is unit-tested on a plain JVM.
 */
internal fun newShopFields(ownerUid: String, name: String, serverTimestamp: Any): Map<String, Any> = mapOf(
    ShopFields.NAME to name,
    ShopFields.OWNER_UID to ownerUid,
    ShopFields.MEMBER_UIDS to listOf(ownerUid),
    ShopFields.CURRENCY to Currency.USD.code,
    ShopFields.CREATED_AT to serverTimestamp,
    ShopFields.UPDATED_AT to serverTimestamp,
    ShopFields.SCHEMA_VERSION to SHOP_SCHEMA_VERSION,
)

/** Display order of a user's shops: by name, then by id so equal names keep a stable order. */
internal fun sortShops(shops: List<Shop>): List<Shop> =
    shops.sortedWith(compareBy<Shop> { it.name }.thenBy { it.id })
