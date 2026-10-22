package com.shopfinance.domain.debts

import com.shopfinance.domain.money.Money
import java.time.LocalDate

/**
 * Money a person took from the shop and has to pay back.
 *
 * @property debtDate the day the person took the money, which may be long before it was
 *   typed into the app.
 */
data class Debt(
    val id: String,
    val debtorId: String,
    val amount: Money,
    val debtDate: LocalDate,
    val note: String = "",
) {
    init {
        require(id.isNotBlank()) { "Debt id must not be blank" }
        require(debtorId.isNotBlank()) { "Debt debtor must not be blank" }
        require(amount.isPositive) { "Debt amount must be positive, was $amount" }
    }
}

/** Part or all of a [Debt] paid back on [paymentDate], always in the debt's currency. */
data class DebtPayment(
    val id: String,
    val debtId: String,
    val amount: Money,
    val paymentDate: LocalDate,
    val note: String = "",
) {
    init {
        require(id.isNotBlank()) { "Payment id must not be blank" }
        require(debtId.isNotBlank()) { "Payment debt must not be blank" }
        require(amount.isPositive) { "Payment amount must be positive, was $amount" }
    }
}

enum class DebtStatus { OPEN, PAID }

/** Why a payment cannot be recorded against a debt. */
enum class PaymentProblem {
    /** Zero or less. */
    NOT_POSITIVE,

    /** More than what is still owed, including on a debt that is already paid. */
    MORE_THAN_REMAINING,

    /** Dated before the day the money was taken. */
    BEFORE_DEBT_DATE,

    /** Dated after today. */
    IN_THE_FUTURE,
}
