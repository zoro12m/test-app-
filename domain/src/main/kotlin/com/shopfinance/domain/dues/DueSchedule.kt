package com.shopfinance.domain.dues

import com.shopfinance.domain.money.Money
import java.time.LocalDate
import java.time.Period

/** One due's share of a payment. */
data class DueAllocation(val due: Due, val applied: Money)

/**
 * The result of spreading a payment over dues.
 *
 * @property allocations updated dues and how much each received, in the order they were paid.
 * @property unallocated whatever was left after every due was settled (an overpayment).
 */
data class PaymentAllocation(val allocations: List<DueAllocation>, val unallocated: Money)

object DueSchedule {

    /** Unpaid dues whose date has passed, most overdue first. */
    fun overdue(dues: Iterable<Due>, today: LocalDate): List<Due> =
        dues.filter { it.statusOn(today) == DueStatus.OVERDUE }.sortedWith(byDate)

    /** Unpaid dues falling between [today] and [today] + [withinDays], inclusive, soonest first. */
    fun upcoming(dues: Iterable<Due>, today: LocalDate, withinDays: Long): List<Due> {
        require(withinDays >= 0) { "withinDays must not be negative" }
        val last = today.plusDays(withinDays)
        return dues
            .filter { !it.isPaid && !it.dueDate.isBefore(today) && !it.dueDate.isAfter(last) }
            .sortedWith(byDate)
    }

    /**
     * Spreads [payment] over the unpaid [dues] in its currency, oldest due date first,
     * the way a shop owner applies a customer's payment to the oldest debt.
     */
    fun allocatePayment(dues: Iterable<Due>, payment: Money): PaymentAllocation {
        require(payment.isPositive) { "Payment must be positive, was $payment" }
        var left = payment
        val allocations = mutableListOf<DueAllocation>()
        for (due in dues.filter { !it.isPaid && it.amount.currency == payment.currency }.sortedWith(byDate)) {
            if (left.isZero) break
            val applied = minOf(left, due.remaining)
            allocations += DueAllocation(due.applyPayment(applied), applied)
            left -= applied
        }
        return PaymentAllocation(allocations, left)
    }

    /**
     * Splits [total] into [count] installments, the first on [firstDueDate] and each next one
     * [interval] later. Amounts add up exactly to [total], with any remainder on the earliest.
     * Dates are counted from the first date, so monthly installments from 31 January fall on
     * 28/29 February, then 31 March.
     */
    fun installments(total: Money, count: Int, firstDueDate: LocalDate, interval: Period): List<Pair<LocalDate, Money>> {
        require(total.isPositive) { "Total must be positive, was $total" }
        require(count > 0) { "count must be positive, was $count" }
        require(!interval.isZero && !interval.isNegative) { "interval must be positive, was $interval" }
        require(total.minor >= count) { "Cannot split $total into $count non-zero installments" }
        return total.split(count).mapIndexed { index, amount ->
            firstDueDate.plus(interval.multipliedBy(index)) to amount
        }
    }

    private val byDate = compareBy<Due>({ it.dueDate }, { it.id })
}
