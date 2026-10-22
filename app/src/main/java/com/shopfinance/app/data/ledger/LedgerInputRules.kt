package com.shopfinance.app.data.ledger

import com.shopfinance.app.data.text.SingleLineText
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.money.Currency
import java.time.Duration
import java.time.Instant

/** Why a [NewEntry] was rejected by [LedgerInputRules.validate]. */
enum class NewEntryError {
    /** Not one of [LedgerInputRules.SUPPORTED_TYPES]. */
    UNSUPPORTED_TYPE,
    NOT_USD,
    NOT_POSITIVE,
    TOO_LARGE,
    MISSING_CATEGORY,

    /** A category on anything but an expense. */
    UNEXPECTED_CATEGORY,
    NOTE_TOO_LONG,
    TOO_EARLY,
    IN_THE_FUTURE,
}

/** Why a void reason was rejected by [LedgerInputRules.validateVoidReason]. */
enum class VoidReasonError { EMPTY, TOO_LONG }

/**
 * Input rules for ledger entries, shared by the forms and the repository. They agree with
 * isValidNewEntry() and isValidVoidReason() in firestore.rules, so anything accepted here
 * is accepted by the server.
 */
object LedgerInputRules {

    /** The entry types the app records for now: cash only, no customers or suppliers yet. */
    val SUPPORTED_TYPES: List<EntryType> =
        listOf(EntryType.SALE, EntryType.EXPENSE, EntryType.CAPITAL_IN, EntryType.CAPITAL_OUT)

    /** 100,000,000.00 USD in cents, the largest single entry. */
    const val MAX_AMOUNT_MINOR = 10_000_000_000L

    const val NOTE_MAX_LENGTH = 200
    const val VOID_REASON_MAX_LENGTH = 200

    /** Entries may not be dated before this. */
    val EARLIEST_DATE: Instant = Instant.parse("2000-01-01T00:00:00Z")

    /**
     * How far after "now" an entry may be dated. The server allows a full day, for clock and
     * time-zone differences; the app keeps a margin under it.
     */
    val FUTURE_TOLERANCE: Duration = Duration.ofHours(12)

    /** Notes are stored on one line, like the other short texts; see [SingleLineText]. */
    fun normalizeNote(raw: String): String = SingleLineText.normalize(raw)

    fun normalizeVoidReason(raw: String): String = SingleLineText.normalize(raw)

    /** Returns the first problem with [entry], or null when it can be stored. [now] is the device's time. */
    fun validate(entry: NewEntry, now: Instant): NewEntryError? = when {
        entry.type !in SUPPORTED_TYPES -> NewEntryError.UNSUPPORTED_TYPE
        entry.amount.currency != Currency.USD -> NewEntryError.NOT_USD
        !entry.amount.isPositive -> NewEntryError.NOT_POSITIVE
        entry.amount.minor > MAX_AMOUNT_MINOR -> NewEntryError.TOO_LARGE
        entry.type == EntryType.EXPENSE && entry.category == null -> NewEntryError.MISSING_CATEGORY
        entry.type != EntryType.EXPENSE && entry.category != null -> NewEntryError.UNEXPECTED_CATEGORY
        normalizeNote(entry.note).length > NOTE_MAX_LENGTH -> NewEntryError.NOTE_TOO_LONG
        entry.occurredAt.isBefore(EARLIEST_DATE) -> NewEntryError.TOO_EARLY
        entry.occurredAt.isAfter(now.plus(FUTURE_TOLERANCE)) -> NewEntryError.IN_THE_FUTURE
        else -> null
    }

    /** Checks [raw] after [normalizeVoidReason]. Returns null when it is a valid reason. */
    fun validateVoidReason(raw: String): VoidReasonError? {
        val reason = normalizeVoidReason(raw)
        return when {
            reason.isEmpty() -> VoidReasonError.EMPTY
            reason.length > VOID_REASON_MAX_LENGTH -> VoidReasonError.TOO_LONG
            else -> null
        }
    }
}
