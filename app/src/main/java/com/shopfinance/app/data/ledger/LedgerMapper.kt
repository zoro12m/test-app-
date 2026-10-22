package com.shopfinance.app.data.ledger

import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.ledger.LedgerEntry
import com.shopfinance.domain.ledger.VoidInfo
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import java.time.Instant
import java.util.Date

/**
 * Collection and field names of a shops/{shopId}/entries/{entryId} document. They must
 * match firestore.rules.
 */
internal object LedgerFields {
    const val COLLECTION = "entries"
    const val TYPE = "type"
    const val AMOUNT_MINOR = "amountMinor"
    const val CURRENCY = "currency"
    const val OCCURRED_AT = "occurredAt"
    const val CATEGORY_ID = "categoryId"

    /** Not written yet: credit entries with customers and suppliers come in a later stage. */
    const val PARTY_ID = "partyId"
    const val NOTE = "note"
    const val CREATED_BY = "createdBy"
    const val CREATED_AT = "createdAt"
    const val SCHEMA_VERSION = "schemaVersion"
    const val VOIDED_AT = "voidedAt"
    const val VOID_REASON = "voidReason"
    const val VOIDED_BY = "voidedBy"
}

/** The schema version new entry documents are written with. */
internal const val ENTRY_SCHEMA_VERSION = 1L

/**
 * Reads an entry document. [data] is the document as Firestore returns it, except that
 * timestamps are already converted to [Instant] (see FirestoreLedgerRepository), so this
 * stays free of Firebase types. Returns null, so the caller skips the document, when a
 * required field is missing or invalid, the currency is not USD, or the entry breaks a
 * domain rule (for example a credit entry without a party).
 */
fun parseEntry(id: String, data: Map<String, Any?>, pendingSync: Boolean): LedgerRecord? {
    if (id.isBlank()) return null
    val type = (data[LedgerFields.TYPE] as? String)
        ?.let { name -> EntryType.entries.firstOrNull { it.wireName == name } }
        ?: return null
    val amountMinor = data[LedgerFields.AMOUNT_MINOR] as? Long ?: return null
    if (data[LedgerFields.CURRENCY] != Currency.USD.code) return null
    val occurredAt = data[LedgerFields.OCCURRED_AT] as? Instant ?: return null
    val createdBy = data[LedgerFields.CREATED_BY] as? String ?: return null
    val note = data[LedgerFields.NOTE] as? String ?: ""
    val categoryId = data[LedgerFields.CATEGORY_ID] as? String
    val voided = (data[LedgerFields.VOID_REASON] as? String)
        ?.takeIf { it.isNotBlank() }
        ?.let { reason ->
            // A void still waiting for the server has an estimated time; fall back just in case.
            val at = data[LedgerFields.VOIDED_AT] as? Instant ?: occurredAt
            VoidInfo(at = at, reason = reason)
        }
    val entry = try {
        LedgerEntry(
            id = id,
            type = type,
            amount = Money(amountMinor, Currency.USD),
            occurredAt = occurredAt,
            partyId = data[LedgerFields.PARTY_ID] as? String,
            categoryId = categoryId,
            note = note,
            voided = voided,
        )
    } catch (e: IllegalArgumentException) {
        return null
    }
    return LedgerRecord(entry = entry, createdBy = createdBy, pendingSync = pendingSync)
}

/**
 * The fields of a new entry document, exactly the keys firestore.rules accepts on create.
 * [entry] must pass [LedgerInputRules.validate]. [serverTimestamp] is Firestore's server
 * timestamp sentinel; it is a parameter so this stays free of Firebase types. occurredAt is
 * a [Date], which Firestore stores as a timestamp.
 */
internal fun newEntryFields(createdBy: String, entry: NewEntry, serverTimestamp: Any): Map<String, Any> = buildMap {
    put(LedgerFields.TYPE, entry.type.wireName)
    put(LedgerFields.AMOUNT_MINOR, entry.amount.minor)
    put(LedgerFields.CURRENCY, entry.amount.currency.code)
    put(LedgerFields.OCCURRED_AT, Date.from(entry.occurredAt))
    entry.category?.let { put(LedgerFields.CATEGORY_ID, it.wireName) }
    put(LedgerFields.NOTE, LedgerInputRules.normalizeNote(entry.note))
    put(LedgerFields.CREATED_BY, createdBy)
    put(LedgerFields.CREATED_AT, serverTimestamp)
    put(LedgerFields.SCHEMA_VERSION, ENTRY_SCHEMA_VERSION)
}

/** The fields a void sets, exactly the keys firestore.rules lets an update change. */
internal fun voidFields(voidedBy: String, reason: String, serverTimestamp: Any): Map<String, Any> = mapOf(
    LedgerFields.VOIDED_AT to serverTimestamp,
    LedgerFields.VOID_REASON to LedgerInputRules.normalizeVoidReason(reason),
    LedgerFields.VOIDED_BY to voidedBy,
)

/** Newest first; entries at the same moment keep a stable order by id. */
internal fun sortRecords(records: List<LedgerRecord>): List<LedgerRecord> =
    records.sortedWith(
        compareByDescending<LedgerRecord> { it.entry.occurredAt }.thenByDescending { it.entry.id },
    )
