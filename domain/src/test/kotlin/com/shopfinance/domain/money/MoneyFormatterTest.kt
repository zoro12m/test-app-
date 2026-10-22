package com.shopfinance.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatterTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun lbp(minor: Long) = Money(minor, Currency.LBP)

    @Test
    fun `latin amounts`() {
        assertEquals("0.00", MoneyFormatter.formatAmount(usd(0)))
        assertEquals("0.05", MoneyFormatter.formatAmount(usd(5)))
        assertEquals("12.50", MoneyFormatter.formatAmount(usd(1_250)))
        assertEquals("1,234.50", MoneyFormatter.formatAmount(usd(123_450)))
        assertEquals("1,234,567.89", MoneyFormatter.formatAmount(usd(123_456_789)))
        assertEquals("-0.05", MoneyFormatter.formatAmount(usd(-5)))
        assertEquals("999", MoneyFormatter.formatAmount(lbp(999)))
        assertEquals("1,000", MoneyFormatter.formatAmount(lbp(1_000)))
        assertEquals("-75,000", MoneyFormatter.formatAmount(lbp(-75_000)))
        assertEquals("1234.50", MoneyFormatter.formatAmount(usd(123_450), grouping = false))
    }

    @Test
    fun `arabic-indic amounts`() {
        assertEquals("١٬٢٣٤٫٥٠", MoneyFormatter.formatAmount(usd(123_450), DigitStyle.ARABIC_INDIC))
        assertEquals("٧٥٬٠٠٠", MoneyFormatter.formatAmount(lbp(75_000), DigitStyle.ARABIC_INDIC))
        assertEquals("٠٫٠٥", MoneyFormatter.formatAmount(usd(5), DigitStyle.ARABIC_INDIC))
    }

    @Test
    fun `extreme values`() {
        assertEquals("-92,233,720,368,547,758.08", MoneyFormatter.formatAmount(usd(Long.MIN_VALUE)))
        assertEquals("9,223,372,036,854,775,807", MoneyFormatter.formatAmount(lbp(Long.MAX_VALUE)))
    }

    @Test
    fun `with currency symbol`() {
        assertEquals("12.50 $", MoneyFormatter.format(usd(1_250)))
        assertEquals("٧٥٬٠٠٠ ل.ل.", MoneyFormatter.format(lbp(75_000), DigitStyle.ARABIC_INDIC))
    }

    @Test
    fun `formatted output parses back to the same amount`() {
        val samples = listOf(usd(0), usd(1), usd(99), usd(100), usd(123_456_789), lbp(1), lbp(89_500), lbp(1_500_000_000))
        for (money in samples) {
            for (style in DigitStyle.entries) {
                val text = MoneyFormatter.formatAmount(money, style)
                assertEquals(
                    MoneyParseResult.Success(money),
                    MoneyParser.parse(text, money.currency, java.math.RoundingMode.UNNECESSARY),
                    "round trip of $text",
                )
            }
        }
    }
}
