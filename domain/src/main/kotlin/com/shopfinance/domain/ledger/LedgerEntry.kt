package com.shopfinance.domain.ledger

import com.shopfinance.domain.money.Money
import java.time.Instant

/** Why and when an entry was cancelled. The entry itself is kept for the audit trail. */
data class VoidInfo(val at: Instant, val reason: String)

/**
 * One line of the shop's append-only ledger. Entries are never edited or deleted: a mistake
 * is fixed by voiding the entry (see [void]) and recording a new one (see [correctWith]).
 *
 * @property id client-generated UUID, so a retried offline write cannot create a duplicate.
 * @property amount always positive; [type] decides which way it moves the balances.
 */
data class LedgerEntry(
    val id: String,
    val type: EntryType,
    val amount: Money,
    val occurredAt: Instant,
    val partyId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val voided: VoidInfo? = null,
) {
    init {
        require(id.isNotBlank()) { "Entry id must not be blank" }
        require(amount.isPositive) { "Entry amount must be positive, was $amount" }
        require(!type.requiresParty || !partyId.isNullOrBlank()) { "${type.wireName} entries need a party" }
        voided?.let { require(it.reason.isNotBlank()) { "Void reason must not be blank" } }
    }

    val isVoided: Boolean get() = voided != null

    /** Returns this entry marked as void. An entry can be voided only once. */
    fun void(at: Instant, reason: String): LedgerEntry {
        check(voided == null) { "Entry $id is already voided" }
        require(reason.isNotBlank()) { "Void reason must not be blank" }
        return copy(voided = VoidInfo(at, reason.trim()))
    }

    /**
     * Fixes this entry: voids it and pairs it with [replacement], which must be a new entry.
     * Both are written together in one batch.
     */
    fun correctWith(replacement: LedgerEntry, at: Instant, reason: String): Correction {
        require(replacement.id != id) { "A correction needs a new entry id" }
        require(!replacement.isVoided) { "The replacement entry must not be voided" }
        return Correction(voided = void(at, reason), replacement = replacement)
    }
}

data class Correction(val voided: LedgerEntry, val replacement: LedgerEntry)
