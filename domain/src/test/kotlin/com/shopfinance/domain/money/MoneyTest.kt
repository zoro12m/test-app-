package com.shopfinance.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun lbp(minor: Long) = Money(minor, Currency.LBP)

    @Test
    fun `adds and subtracts in minor units`() {
        assertEquals(usd(1_050), usd(1_000) + usd(50))
        assertEquals(usd(-50), usd(1_000) - usd(1_050))
        assertEquals(usd(3_000), usd(1_000) * 3)
        assertEquals(usd(-5), -usd(5))
        assertEquals(usd(5), usd(-5).abs())
    }

    @Test
    fun `classic float error does not happen`() {
        // 0.1 + 0.2 != 0.3 with Double; here it is exact.
        val sum = Money.ofMajor("0.10", Currency.USD) + Money.ofMajor("0.20", Currency.USD)
        assertEquals(Money.ofMajor("0.30", Currency.USD), sum)
        assertEquals(30, sum.minor)
    }

    @Test
    fun `mixing currencies throws`() {
        val e = assertFailsWith<CurrencyMismatchException> { usd(1) + lbp(1) }
        assertEquals(Currency.USD, e.expected)
        assertEquals(Currency.LBP, e.actual)
        assertFailsWith<CurrencyMismatchException> { usd(1) - lbp(1) }
        assertFailsWith<CurrencyMismatchException> { usd(1) < lbp(2) }
    }

    @Test
    fun `overflow throws instead of wrapping`() {
        assertFailsWith<ArithmeticException> { usd(Long.MAX_VALUE) + usd(1) }
        assertFailsWith<ArithmeticException> { usd(Long.MIN_VALUE) - usd(1) }
        assertFailsWith<ArithmeticException> { usd(Long.MAX_VALUE / 2 + 1) * 2 }
        assertFailsWith<ArithmeticException> { -usd(Long.MIN_VALUE) }
        assertFailsWith<ArithmeticException> { usd(Long.MIN_VALUE).abs() }
    }

    @Test
    fun `compares within a currency`() {
        assertTrue(usd(1) < usd(2))
        assertTrue(lbp(90_000) > lbp(89_999))
        assertEquals(usd(7), maxOf(usd(7), usd(3)))
    }

    @Test
    fun `ofMajor rounds to the currency minor unit`() {
        assertEquals(usd(1_235), Money.ofMajor("12.345", Currency.USD, RoundingMode.HALF_UP))
        assertEquals(usd(1_234), Money.ofMajor("12.345", Currency.USD, RoundingMode.HALF_EVEN))
        assertEquals(usd(1_234), Money.ofMajor("12.349", Currency.USD, RoundingMode.DOWN))
        assertEquals(lbp(1_501), Money.ofMajor("1500.5", Currency.LBP, RoundingMode.HALF_UP))
        assertEquals(lbp(89_500), Money.ofMajor("89500", Currency.LBP))
        assertEquals(usd(-1_235), Money.ofMajor("-12.345", Currency.USD, RoundingMode.HALF_UP))
    }

    @Test
    fun `ofMajor with UNNECESSARY rejects extra decimals`() {
        assertFailsWith<ArithmeticException> { Money.ofMajor("1.005", Currency.USD, RoundingMode.UNNECESSARY) }
        assertEquals(usd(100), Money.ofMajor("1.000", Currency.USD, RoundingMode.UNNECESSARY))
    }

    @Test
    fun `ofMajor rejects amounts that do not fit in a Long`() {
        assertFailsWith<ArithmeticException> { Money.ofMajor("92233720368547758.08", Currency.USD) }
        assertEquals(usd(Long.MAX_VALUE), Money.ofMajor("92233720368547758.07", Currency.USD))
    }

    @Test
    fun `toMajor and toString are exact`() {
        assertEquals(BigDecimal("12.05"), usd(1_205).toMajor())
        assertEquals(BigDecimal("89500"), lbp(89_500).toMajor())
        assertEquals("0.05 USD", usd(5).toString())
        assertEquals("-89500 LBP", lbp(-89_500).toString())
    }

    @Test
    fun `split adds up exactly and spreads the remainder`() {
        assertEquals(listOf(usd(334), usd(333), usd(333)), usd(1_000).split(3))
        assertEquals(listOf(usd(-334), usd(-333), usd(-333)), usd(-1_000).split(3))
        assertEquals(listOf(lbp(50_000), lbp(50_000)), lbp(100_000).split(2))
        assertEquals(listOf(usd(1), usd(0), usd(0)), usd(1).split(3))
        assertFailsWith<IllegalArgumentException> { usd(1).split(0) }

        for (total in listOf(1L, 7L, 999L, 1_000_001L, Long.MAX_VALUE)) {
            for (parts in 1..13) {
                val pieces = usd(total).split(parts)
                assertEquals(parts, pieces.size)
                assertEquals(usd(total), pieces.reduce(Money::plus))
                assertTrue(pieces.maxOf { it.minor } - pieces.minOf { it.minor } <= 1)
            }
        }
    }

    @Test
    fun `convert needs an explicit rate and rounds`() {
        // 10.00 USD at 89,500 LBP per USD
        assertEquals(lbp(895_000), usd(1_000).convert(Currency.LBP, BigDecimal("89500")))
        // 100,000 LBP back to USD: 1.117318... -> 1.12
        assertEquals(usd(112), lbp(100_000).convert(Currency.USD, BigDecimal.ONE.divide(BigDecimal("89500"), 10, RoundingMode.HALF_UP)))
        assertFailsWith<IllegalArgumentException> { usd(1).convert(Currency.LBP, BigDecimal.ZERO) }
    }

    @Test
    fun `currency lookup by code`() {
        assertEquals(Currency.USD, Currency.fromCode("usd"))
        assertEquals(Currency.LBP, Currency.fromCode(" LBP "))
        assertFailsWith<IllegalArgumentException> { Currency.fromCode("EUR") }
    }
}
