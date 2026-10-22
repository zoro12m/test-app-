package com.shopfinance.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTotalsTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun lbp(minor: Long) = Money(minor, Currency.LBP)

    @Test
    fun `keeps each currency separate`() {
        val totals = MoneyTotals.of(usd(1_000), lbp(500_000), usd(250))
        assertEquals(usd(1_250), totals[Currency.USD])
        assertEquals(lbp(500_000), totals[Currency.LBP])
        assertEquals(listOf(Currency.USD, Currency.LBP), totals.currencies)
    }

    @Test
    fun `missing currency reads as zero`() {
        assertEquals(lbp(0), MoneyTotals.of(usd(5))[Currency.LBP])
        assertTrue(MoneyTotals.EMPTY.isZero)
    }

    @Test
    fun `zero totals are dropped and equality ignores them`() {
        val totals = MoneyTotals.of(usd(100), lbp(10)) - usd(100)
        assertEquals(listOf(Currency.LBP), totals.currencies)
        assertEquals(MoneyTotals.of(lbp(10)), totals)
        assertEquals(MoneyTotals.EMPTY, MoneyTotals.of(usd(5), usd(-5)))
    }

    @Test
    fun `adds and subtracts whole totals`() {
        val a = MoneyTotals.of(usd(100), lbp(1_000))
        val b = MoneyTotals.of(usd(30), lbp(2_000))
        assertEquals(MoneyTotals.of(usd(130), lbp(3_000)), a + b)
        assertEquals(MoneyTotals.of(usd(70), lbp(-1_000)), a - b)
        assertEquals(MoneyTotals.of(usd(-100), lbp(-1_000)), -a)
        assertEquals(listOf(usd(70), lbp(-1_000)), (a - b).toList())
    }

    @Test
    fun `overflow throws`() {
        assertFailsWith<ArithmeticException> { MoneyTotals.of(usd(Long.MAX_VALUE)) + usd(1) }
    }
}
