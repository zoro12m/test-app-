package com.shopfinance.app.data.debts

import com.shopfinance.app.data.text.SingleLineText
import com.shopfinance.domain.money.Currency
import java.time.LocalDate

/** Why a [NewDebtor] was rejected by [DebtInputRules.validateDebtor]. */
enum class NewDebtorError { NAME_EMPTY, NAME_TOO_LONG, PHONE_INVALID, NOTE_TOO_LONG }

/** Why a [NewDebt] was rejected by [DebtInputRules.validateDebt]. */
enum class NewDebtError { UNSUPPORTED_CURRENCY, NOT_POSITIVE, TOO_LARGE, NOTE_TOO_LONG, TOO_EARLY, IN_THE_FUTURE }

/**
 * Input rules for debtors, debts and payments, shared by the forms and the repository. They
 * agree with isValidNewDebtor(), isValidNewDebt() and isValidNewPayment() in firestore.rules,
 * so anything accepted here is accepted by the server. Whether a payment fits what is still
 * owed is checked by [com.shopfinance.domain.debts.DebtAccount.checkPayment].
 */
object DebtInputRules {

    /** Debts may be in either currency; totals are always kept per currency. */
    val CURRENCIES: List<Currency> = listOf(Currency.USD, Currency.LBP)

    /** The largest single debt: 100,000,000.00 USD in cents, or 10,000,000,000 LBP. */
    const val MAX_AMOUNT_MINOR = 10_000_000_000L

    const val NAME_MAX_LENGTH = 60
    const val PHONE_MAX_LENGTH = 20
    const val NOTE_MAX_LENGTH = 200

    /** Debts may not be dated before this day. */
    val EARLIEST_DAY: LocalDate = LocalDate.of(2000, 1, 1)

    private val PHONE_PATTERN = Regex("[0-9 +-]{1,$PHONE_MAX_LENGTH}")

    fun normalizeName(raw: String): String = SingleLineText.normalize(raw)

    fun normalizeNote(raw: String): String = SingleLineText.normalize(raw)

    /**
     * [raw] on one line with Arabic-Indic digits (٠١٢ and ۰۱۲) turned into 0-9, the only digits
     * the server accepts. An empty result means no phone.
     */
    fun normalizePhone(raw: String): String {
        val western = buildString(raw.length) {
            for (char in raw) {
                append(
                    when (char) {
                        in '\u0660'..'\u0669' -> '0' + (char - '\u0660')
                        in '\u06F0'..'\u06F9' -> '0' + (char - '\u06F0')
                        else -> char
                    },
                )
            }
        }
        return SingleLineText.normalize(western)
    }

    /** What is wrong with the typed name, or null when it can be stored. */
    fun nameError(raw: String): NewDebtorError? {
        val name = normalizeName(raw)
        return when {
            name.isEmpty() -> NewDebtorError.NAME_EMPTY
            name.length > NAME_MAX_LENGTH -> NewDebtorError.NAME_TOO_LONG
            else -> null
        }
    }

    /** True when the typed phone, once normalized, is empty or a number the server accepts. */
    fun isValidPhone(raw: String): Boolean {
        val phone = normalizePhone(raw)
        return phone.isEmpty() || (PHONE_PATTERN.matches(phone) && phone.any { it in '0'..'9' })
    }

    fun noteTooLong(raw: String): Boolean = normalizeNote(raw).length > NOTE_MAX_LENGTH

    /** Returns the first problem with [debtor], or null when it can be stored. */
    fun validateDebtor(debtor: NewDebtor): NewDebtorError? = when {
        nameError(debtor.name) != null -> nameError(debtor.name)
        !isValidPhone(debtor.phone) -> NewDebtorError.PHONE_INVALID
        noteTooLong(debtor.note) -> NewDebtorError.NOTE_TOO_LONG
        else -> null
    }

    /** Returns the first problem with [debt], or null when it can be stored. [today] is the shop's calendar day. */
    fun validateDebt(debt: NewDebt, today: LocalDate): NewDebtError? = when {
        debt.amount.currency !in CURRENCIES -> NewDebtError.UNSUPPORTED_CURRENCY
        !debt.amount.isPositive -> NewDebtError.NOT_POSITIVE
        debt.amount.minor > MAX_AMOUNT_MINOR -> NewDebtError.TOO_LARGE
        noteTooLong(debt.note) -> NewDebtError.NOTE_TOO_LONG
        debt.debtDate.isBefore(EARLIEST_DAY) -> NewDebtError.TOO_EARLY
        debt.debtDate.isAfter(today) -> NewDebtError.IN_THE_FUTURE
        else -> null
    }
}
