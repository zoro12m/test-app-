package com.shopfinance.app.data.ledger

import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.ledger.LedgerEntry
import com.shopfinance.domain.money.Money
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * A ledger entry as the app shows it: the domain entry plus who recorded it and whether the
 * device still has to send it (or its void) to the server.
 */
data class LedgerRecord(
    val entry: LedgerEntry,
    val createdBy: String,
    val pendingSync: Boolean,
)

/**
 * Every entry of a shop, newest first. [fromCache] is true while the list came only from the
 * device cache, so it may be missing entries recorded on another device.
 */
data class LedgerSnapshot(
    val records: List<LedgerRecord>,
    val fromCache: Boolean,
) {
    val hasPendingWrites: Boolean get() = records.any { it.pendingSync }
}

/** A cash entry typed by the person, before it is stored. See [LedgerInputRules.validate]. */
data class NewEntry(
    val type: EntryType,
    val amount: Money,
    val occurredAt: Instant,
    /** Required for [EntryType.EXPENSE], and must be null for every other type. */
    val category: ExpenseCategory?,
    val note: String,
)

/** The server refused a write that the device had already applied locally. */
data class LedgerRejection(
    val kind: Kind,
    val entryId: String,
    /** Firestore said PERMISSION_DENIED, usually because the security rules are not published. */
    val accessDenied: Boolean,
) {
    enum class Kind { ADD, VOID }
}

interface LedgerRepository {
    /**
     * Live list of every entry of the shop [shopId], newest first. The flow fails with
     * [com.shopfinance.app.data.shop.ShopAccessException] when Firestore refuses the read.
     */
    fun observeEntries(shopId: String): Flow<LedgerSnapshot>

    /**
     * Records [entry] in the shop [shopId] and returns its new id at once. Like
     * [com.shopfinance.app.data.shop.ShopRepository.createShop] the write is queued locally,
     * so it works offline and shows up in [observeEntries] immediately.
     *
     * @throws IllegalArgumentException when [entry] fails [LedgerInputRules.validate]; the UI
     *   validates first.
     */
    fun addEntry(shopId: String, createdBy: String, entry: NewEntry): String

    /**
     * Voids the entry [entryId] with [reason], keeping it for the audit trail. Queued locally
     * like [addEntry].
     *
     * @throws IllegalArgumentException when [reason] fails [LedgerInputRules.validateVoidReason].
     */
    fun voidEntry(shopId: String, entryId: String, voidedBy: String, reason: String)

    /** Writes the server refused after they were applied locally. Firestore has undone them on the device. */
    val rejections: Flow<LedgerRejection>
}
