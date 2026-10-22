package com.shopfinance.app.data.debts

import com.shopfinance.domain.debts.Debt
import com.shopfinance.domain.debts.DebtPayment
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Collection and field names of the debt documents under shops/{shopId}. They must match
 * firestore.rules.
 */
internal object DebtFields {
    const val DEBTORS = "debtors"
    const val DEBTS = "debts"
    const val PAYMENTS = "debtPayments"

    const val NAME = "name"
    const val PHONE = "phone"
    const val DEBTOR_ID = "debtorId"
    const val DEBT_ID = "debtId"
    const val AMOUNT_MINOR = "amountMinor"
    const val CURRENCY = "currency"
    const val DEBT_DATE = "debtDate"
    const val PAYMENT_DATE = "paymentDate"
    const val NOTE = "note"
    const val CREATED_BY = "createdBy"
    const val CREATED_AT = "createdAt"
    const val SCHEMA_VERSION = "schemaVersion"
    const val DELETED_AT = "deletedAt"
    const val DELETED_BY = "deletedBy"
}

/** The schema version new debtor, debt and payment documents are written with. */
internal const val DEBT_SCHEMA_VERSION = 1L

private val DAY_PATTERN = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

/** A calendar day as stored: "2026-10-01". */
internal fun formatDay(day: LocalDate): String = day.toString()

/** Reads a stored calendar day, or null when [value] is not one. */
internal fun parseDay(value: Any?): LocalDate? {
    val text = value as? String ?: return null
    if (!DAY_PATTERN.matches(text)) return null
    return try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** A payment document before it is matched with its debt, which gives it a currency. */
internal data class StoredPayment(
    val id: String,
    val debtId: String,
    val amountMinor: Long,
    val paymentDate: LocalDate,
    val note: String,
    val createdAt: Instant,
    val pendingSync: Boolean,
)

/*
 * The parse functions read a document whose timestamps are already converted to [Instant]
 * (see FirestoreDebtRepository), so they stay free of Firebase types. Each returns null, so
 * the caller skips the document, when a required field is missing or invalid, or when the
 * document is marked deleted.
 */

fun parseDebtor(id: String, data: Map<String, Any?>, pendingSync: Boolean): DebtorRecord? {
    if (id.isBlank() || DebtFields.DELETED_AT in data) return null
    val name = (data[DebtFields.NAME] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val createdAt = data[DebtFields.CREATED_AT] as? Instant ?: return null
    return DebtorRecord(
        id = id,
        name = name,
        phone = (data[DebtFields.PHONE] as? String)?.takeIf { it.isNotBlank() },
        note = data[DebtFields.NOTE] as? String ?: "",
        createdAt = createdAt,
        pendingSync = pendingSync,
    )
}

fun parseDebt(id: String, data: Map<String, Any?>, pendingSync: Boolean): DebtRecord? {
    if (id.isBlank() || DebtFields.DELETED_AT in data) return null
    val debtorId = data[DebtFields.DEBTOR_ID] as? String ?: return null
    val amountMinor = data[DebtFields.AMOUNT_MINOR] as? Long ?: return null
    val currency = DebtInputRules.CURRENCIES.firstOrNull { it.code == data[DebtFields.CURRENCY] } ?: return null
    val debtDate = parseDay(data[DebtFields.DEBT_DATE]) ?: return null
    val createdAt = data[DebtFields.CREATED_AT] as? Instant ?: return null
    val debt = try {
        Debt(
            id = id,
            debtorId = debtorId,
            amount = Money(amountMinor, currency),
            debtDate = debtDate,
            note = data[DebtFields.NOTE] as? String ?: "",
        )
    } catch (e: IllegalArgumentException) {
        return null
    }
    return DebtRecord(debt = debt, createdAt = createdAt, pendingSync = pendingSync)
}

internal fun parsePayment(id: String, data: Map<String, Any?>, pendingSync: Boolean): StoredPayment? {
    if (id.isBlank() || DebtFields.DELETED_AT in data) return null
    val debtId = (data[DebtFields.DEBT_ID] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val amountMinor = (data[DebtFields.AMOUNT_MINOR] as? Long)?.takeIf { it > 0 } ?: return null
    val paymentDate = parseDay(data[DebtFields.PAYMENT_DATE]) ?: return null
    val createdAt = data[DebtFields.CREATED_AT] as? Instant ?: return null
    return StoredPayment(
        id = id,
        debtId = debtId,
        amountMinor = amountMinor,
        paymentDate = paymentDate,
        note = data[DebtFields.NOTE] as? String ?: "",
        createdAt = createdAt,
        pendingSync = pendingSync,
    )
}

/**
 * Puts the three collections together. Payments take the currency of their debt; payments
 * whose debt is missing or deleted are left out, so deleting a debt hides its payments.
 */
internal fun buildDebtsSnapshot(
    debtors: List<DebtorRecord>,
    debts: List<DebtRecord>,
    payments: List<StoredPayment>,
    fromCache: Boolean,
): DebtsSnapshot {
    val currencyOf: Map<String, Currency> = debts.associate { it.debt.id to it.debt.amount.currency }
    val paymentRecords = payments
        .sortedWith(compareByDescending<StoredPayment> { it.createdAt }.thenByDescending { it.id })
        .mapNotNull { stored ->
            val currency = currencyOf[stored.debtId] ?: return@mapNotNull null
            PaymentRecord(
                payment = DebtPayment(
                    id = stored.id,
                    debtId = stored.debtId,
                    amount = Money(stored.amountMinor, currency),
                    paymentDate = stored.paymentDate,
                    note = stored.note,
                ),
                createdAt = stored.createdAt,
                pendingSync = stored.pendingSync,
            )
        }
    return DebtsSnapshot(
        debtors = debtors.sortedWith(compareBy<DebtorRecord> { it.name.lowercase() }.thenBy { it.id }),
        debts = debts.sortedWith(compareByDescending<DebtRecord> { it.createdAt }.thenByDescending { it.debt.id }),
        payments = paymentRecords,
        fromCache = fromCache,
    )
}

/**
 * The fields of a new debtor document, exactly the keys firestore.rules accepts on create.
 * [debtor] must pass [DebtInputRules.validateDebtor]. [serverTimestamp] is Firestore's server
 * timestamp sentinel; it is a parameter so this stays free of Firebase types.
 */
internal fun newDebtorFields(createdBy: String, debtor: NewDebtor, serverTimestamp: Any): Map<String, Any> = buildMap {
    put(DebtFields.NAME, DebtInputRules.normalizeName(debtor.name))
    DebtInputRules.normalizePhone(debtor.phone).takeIf { it.isNotEmpty() }?.let { put(DebtFields.PHONE, it) }
    put(DebtFields.NOTE, DebtInputRules.normalizeNote(debtor.note))
    put(DebtFields.CREATED_BY, createdBy)
    put(DebtFields.CREATED_AT, serverTimestamp)
    put(DebtFields.SCHEMA_VERSION, DEBT_SCHEMA_VERSION)
}

/** The fields of a new debt document; [debt] must pass [DebtInputRules.validateDebt]. */
internal fun newDebtFields(createdBy: String, debt: NewDebt, serverTimestamp: Any): Map<String, Any> = mapOf(
    DebtFields.DEBTOR_ID to debt.debtorId,
    DebtFields.AMOUNT_MINOR to debt.amount.minor,
    DebtFields.CURRENCY to debt.amount.currency.code,
    DebtFields.DEBT_DATE to formatDay(debt.debtDate),
    DebtFields.NOTE to DebtInputRules.normalizeNote(debt.note),
    DebtFields.CREATED_BY to createdBy,
    DebtFields.CREATED_AT to serverTimestamp,
    DebtFields.SCHEMA_VERSION to DEBT_SCHEMA_VERSION,
)

/** The fields of a new payment document. The currency is the debt's, so it is not stored. */
internal fun newPaymentFields(createdBy: String, debtId: String, payment: NewPayment, serverTimestamp: Any): Map<String, Any> =
    mapOf(
        DebtFields.DEBT_ID to debtId,
        DebtFields.AMOUNT_MINOR to payment.amount.minor,
        DebtFields.PAYMENT_DATE to formatDay(payment.paymentDate),
        DebtFields.NOTE to DebtInputRules.normalizeNote(payment.note),
        DebtFields.CREATED_BY to createdBy,
        DebtFields.CREATED_AT to serverTimestamp,
        DebtFields.SCHEMA_VERSION to DEBT_SCHEMA_VERSION,
    )

/** The fields a delete sets on a debt or payment, exactly the keys firestore.rules lets an update change. */
internal fun deleteFields(deletedBy: String, serverTimestamp: Any): Map<String, Any> = mapOf(
    DebtFields.DELETED_AT to serverTimestamp,
    DebtFields.DELETED_BY to deletedBy,
)
