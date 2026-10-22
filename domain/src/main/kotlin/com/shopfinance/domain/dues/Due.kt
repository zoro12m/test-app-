package com.shopfinance.domain.dues

import com.shopfinance.domain.money.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DueDirection(val wireName: String) {
    /** A customer has to pay the shop. */
    RECEIVABLE("receivable"),

    /** The shop has to pay a supplier or lender. */
    PAYABLE("payable"),
}

enum class DueStatus(val wireName: String) { OPEN("open"), PARTIAL("partial"), PAID("paid"), OVERDUE("overdue") }

/**
 * An amount that has to be paid by a given date, used for reminders.
 *
 * @property paid how much of [amount] has been paid so far; never more than [amount].
 */
data class Due(
    val id: String,
    val partyId: String,
    val direction: DueDirection,
    val amount: Money,
    val dueDate: LocalDate,
    val paid: Money = Money.zero(amount.currency),
) {
    init {
        require(id.isNotBlank()) { "Due id must not be blank" }
        require(partyId.isNotBlank()) { "Due party must not be blank" }
        require(amount.isPositive) { "Due amount must be positive, was $amount" }
        amount.requireSameCurrency(paid)
        require(!paid.isNegative && paid <= amount) { "Paid $paid must be between 0 and $amount" }
    }

    val remaining: Money get() = amount - paid

    val isPaid: Boolean get() = paid == amount

    /** Status on [today]. A due is overdue from the day after [dueDate] until fully paid. */
    fun statusOn(today: LocalDate): DueStatus = when {
        isPaid -> DueStatus.PAID
        today.isAfter(dueDate) -> DueStatus.OVERDUE
        paid.isPositive -> DueStatus.PARTIAL
        else -> DueStatus.OPEN
    }

    /** Days from [today] to [dueDate]: 0 on the day, negative once it has passed. */
    fun daysUntilDue(today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)

    /** Records a payment against this due. It must be positive and no more than [remaining]. */
    fun applyPayment(payment: Money): Due {
        amount.requireSameCurrency(payment)
        require(payment.isPositive) { "Payment must be positive, was $payment" }
        require(payment <= remaining) { "Payment $payment is more than the remaining $remaining" }
        return copy(paid = paid + payment)
    }
}
