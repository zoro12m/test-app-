package com.shopfinance.domain.dues

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import java.time.LocalDate
import java.time.Period
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DueScheduleTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun lbp(minor: Long) = Money(minor, Currency.LBP)
    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun due(id: String, amount: Money, on: LocalDate, paid: Money = Money.zero(amount.currency)) =
        Due(id, "c1", DueDirection.RECEIVABLE, amount, on, paid)

    private val today = date(10, 22)

    @Test
    fun `overdue and upcoming lists`() {
        val dues = listOf(
            due("late-2", usd(100), date(10, 20)),
            due("late-1", usd(100), date(10, 1)),
            due("late-paid", usd(100), date(10, 1), paid = usd(100)),
            due("today", usd(100), today),
            due("in-3", usd(100), date(10, 25)),
            due("in-7", usd(100), date(10, 29)),
            due("in-8", usd(100), date(10, 30)),
            due("soon-paid", usd(100), date(10, 24), paid = usd(100)),
        )
        assertEquals(listOf("late-1", "late-2"), DueSchedule.overdue(dues, today).map { it.id })
        assertEquals(listOf("today", "in-3", "in-7"), DueSchedule.upcoming(dues, today, withinDays = 7).map { it.id })
        assertEquals(listOf("today"), DueSchedule.upcoming(dues, today, withinDays = 0).map { it.id })
        assertFailsWith<IllegalArgumentException> { DueSchedule.upcoming(dues, today, withinDays = -1) }
    }

    @Test
    fun `payment goes to the oldest due first`() {
        val dues = listOf(
            due("nov", usd(5_000), date(11, 1)),
            due("sep", usd(3_000), date(9, 1), paid = usd(1_000)),
            due("oct", usd(4_000), date(10, 1)),
        )
        val result = DueSchedule.allocatePayment(dues, usd(7_000))
        assertEquals(listOf("sep", "oct", "nov"), result.allocations.map { it.due.id })
        assertEquals(listOf(usd(2_000), usd(4_000), usd(1_000)), result.allocations.map { it.applied })
        assertEquals(listOf(true, true, false), result.allocations.map { it.due.isPaid })
        assertEquals(usd(4_000), result.allocations[2].due.remaining)
        assertEquals(usd(0), result.unallocated)
    }

    @Test
    fun `overpayment is reported, not lost`() {
        val result = DueSchedule.allocatePayment(listOf(due("a", usd(1_000), date(10, 1))), usd(1_500))
        assertEquals(usd(1_000), result.allocations.single().applied)
        assertEquals(usd(500), result.unallocated)
    }

    @Test
    fun `allocation skips paid dues and other currencies`() {
        val dues = listOf(
            due("paid", usd(1_000), date(9, 1), paid = usd(1_000)),
            due("lbp", lbp(500_000), date(9, 2)),
            due("usd", usd(1_000), date(9, 3)),
        )
        val result = DueSchedule.allocatePayment(dues, usd(400))
        assertEquals(listOf("usd"), result.allocations.map { it.due.id })
        assertTrue(result.unallocated.isZero)
        assertFailsWith<IllegalArgumentException> { DueSchedule.allocatePayment(dues, usd(0)) }
    }

    @Test
    fun `money is conserved by allocation`() {
        val dues = (1..10).map { due("d$it", usd(it * 1_237L), date(10, it)) }
        for (payment in listOf(1L, 999L, 12_345L, 68_035L, 100_000L)) {
            val result = DueSchedule.allocatePayment(dues, usd(payment))
            val applied = result.allocations.map { it.applied }.fold(usd(0), Money::plus)
            assertEquals(usd(payment), applied + result.unallocated)
        }
    }

    @Test
    fun `monthly installments clamp month ends and add up`() {
        val plan = DueSchedule.installments(usd(100_000), 3, date(1, 31), Period.ofMonths(1))
        assertEquals(listOf(date(1, 31), date(2, 28), date(3, 31)), plan.map { it.first })
        assertEquals(listOf(usd(33_334), usd(33_333), usd(33_333)), plan.map { it.second })
    }

    @Test
    fun `weekly installments in LBP`() {
        val plan = DueSchedule.installments(lbp(1_000_000), 4, date(10, 22), Period.ofWeeks(1))
        assertEquals(listOf(date(10, 22), date(10, 29), date(11, 5), date(11, 12)), plan.map { it.first })
        assertTrue(plan.all { it.second == lbp(250_000) })
    }

    @Test
    fun `installments reject bad input`() {
        assertFailsWith<IllegalArgumentException> { DueSchedule.installments(usd(0), 3, today, Period.ofMonths(1)) }
        assertFailsWith<IllegalArgumentException> { DueSchedule.installments(usd(100), 0, today, Period.ofMonths(1)) }
        assertFailsWith<IllegalArgumentException> { DueSchedule.installments(usd(100), 2, today, Period.ZERO) }
        assertFailsWith<IllegalArgumentException> { DueSchedule.installments(usd(2), 3, today, Period.ofDays(1)) }
    }
}
