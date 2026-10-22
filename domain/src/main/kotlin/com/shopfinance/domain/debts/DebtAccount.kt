package com.shopfinance.domain.debts

import com.shopfinance.domain.money.Money
import java.time.LocalDate

/**
 * A debt together with its payments. What has been paid and what remains are always worked
 * out from the payments here; they are never stored.
 *
 * @property payments the debt's payments, newest payment date first. Payments on the same
 *   day keep the order they were given in.
 */
class DebtAccount(val debt: Debt, payments: List<DebtPayment>) {

    val payments: List<DebtPayment> = payments.sortedByDescending { it.paymentDate }

    init {
        for (payment in this.payments) {
            require(payment.debtId == debt.id) { "Payment ${payment.id} belongs to ${payment.debtId}, not ${debt.id}" }
            debt.amount.requireSameCurrency(payment.amount)
        }
    }

    val paid: Money = this.payments.fold(Money.zero(debt.amount.currency)) { sum, payment -> sum + payment.amount }

    /**
     * Debt amount minus everything paid. Negative only if payments recorded on two phones at
     * once add up to more than the debt; see [overpaid].
     */
    val remaining: Money = debt.amount - paid

    val status: DebtStatus = if (remaining.isPositive) DebtStatus.OPEN else DebtStatus.PAID

    val isOpen: Boolean get() = status == DebtStatus.OPEN

    /** How much more than the debt was paid, or null when nothing was overpaid. */
    val overpaid: Money? = if (remaining.isNegative) -remaining else null

    /**
     * Why a payment of [amount] dated [paymentDate] cannot be recorded, or null when it can.
     * [today] is the shop's calendar day.
     */
    fun checkPayment(amount: Money, paymentDate: LocalDate, today: LocalDate): PaymentProblem? {
        debt.amount.requireSameCurrency(amount)
        return when {
            !amount.isPositive -> PaymentProblem.NOT_POSITIVE
            amount > remaining -> PaymentProblem.MORE_THAN_REMAINING
            paymentDate.isBefore(debt.debtDate) -> PaymentProblem.BEFORE_DEBT_DATE
            paymentDate.isAfter(today) -> PaymentProblem.IN_THE_FUTURE
            else -> null
        }
    }

    /** This account with [payment] added. Throws if [checkPayment] finds a problem with it. */
    fun record(payment: DebtPayment, today: LocalDate): DebtAccount {
        require(payment.debtId == debt.id) { "Payment ${payment.id} belongs to ${payment.debtId}, not ${debt.id}" }
        val problem = checkPayment(payment.amount, payment.paymentDate, today)
        require(problem == null) { "Payment ${payment.amount} on ${payment.paymentDate} refused: $problem" }
        return DebtAccount(debt, listOf(payment) + payments)
    }

    override fun equals(other: Any?): Boolean =
        other is DebtAccount && other.debt == debt && other.payments == payments

    override fun hashCode(): Int = 31 * debt.hashCode() + payments.hashCode()

    override fun toString(): String = "DebtAccount(${debt.id}, remaining=$remaining, $status)"
}
