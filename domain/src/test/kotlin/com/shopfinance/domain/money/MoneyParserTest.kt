package com.shopfinance.domain.money

import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyParserTest {

    private fun ok(input: String, currency: Currency, rounding: RoundingMode = RoundingMode.HALF_UP): Long {
        val result = MoneyParser.parse(input, currency, rounding)
        check(result is MoneyParseResult.Success) { "Expected success for '$input', got $result" }
        assertEquals(currency, result.money.currency)
        return result.money.minor
    }

    private fun fails(input: String, currency: Currency = Currency.USD, rounding: RoundingMode = RoundingMode.HALF_UP) =
        (MoneyParser.parse(input, currency, rounding) as MoneyParseResult.Failure).error

    @Test
    fun `plain western digits`() {
        assertEquals(1_250, ok("12.50", Currency.USD))
        assertEquals(1_200, ok("12", Currency.USD))
        assertEquals(1_250, ok("12.5", Currency.USD))
        assertEquals(1_200, ok("12.", Currency.USD))
        assertEquals(50, ok(".5", Currency.USD))
        assertEquals(0, ok("0", Currency.USD))
        assertEquals(89_500, ok("89500", Currency.LBP))
    }

    @Test
    fun `arabic-indic digits and separators`() {
        assertEquals(1_250, ok("١٢٫٥٠", Currency.USD))
        assertEquals(1_500_000, ok("١٬٥٠٠٬٠٠٠", Currency.LBP))
        assertEquals(1_500_000, ok("١،٥٠٠،٠٠٠", Currency.LBP))
        assertEquals(75_000, ok("٧٥٠٠٠", Currency.LBP))
    }

    @Test
    fun `eastern arabic-indic digits`() {
        assertEquals(1_234, ok("۱۲٫۳۴", Currency.USD))
    }

    @Test
    fun `mixed digit scripts are accepted`() {
        assertEquals(1_234, ok("1٢.3۴", Currency.USD))
    }

    @Test
    fun `grouping separators and whitespace`() {
        assertEquals(123_456_789, ok("1,234,567.89", Currency.USD))
        assertEquals(1_000_000, ok(" 1 000 000 ", Currency.LBP))
        assertEquals(1_000_000, ok("1 000 000", Currency.LBP))
        assertEquals(1_250, ok("‏12.50‎", Currency.USD))
    }

    @Test
    fun `ambiguous or malformed grouping is rejected`() {
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("1,5"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("1,50,000"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("1234,567"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails(",500"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("1.000,50"))
    }

    @Test
    fun `garbage is rejected`() {
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("abc"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("$12"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("12$"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("1.2.3"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("1e5"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("+5"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("."))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("--5"))
        assertEquals(MoneyParseError.INVALID_FORMAT, fails("5-"))
    }

    @Test
    fun `empty input`() {
        assertEquals(MoneyParseError.EMPTY, fails(""))
        assertEquals(MoneyParseError.EMPTY, fails("   "))
        assertEquals(MoneyParseError.EMPTY, fails("‏"))
    }

    @Test
    fun `negative amounts are rejected`() {
        assertEquals(MoneyParseError.NEGATIVE, fails("-5"))
        assertEquals(MoneyParseError.NEGATIVE, fails("−5.00"))
        assertEquals(MoneyParseError.NEGATIVE, fails("-٥"))
    }

    @Test
    fun `extra decimals are rounded with the given mode`() {
        assertEquals(1_235, ok("12.345", Currency.USD, RoundingMode.HALF_UP))
        assertEquals(1_234, ok("12.345", Currency.USD, RoundingMode.HALF_EVEN))
        assertEquals(1_501, ok("1500.5", Currency.LBP, RoundingMode.HALF_UP))
        assertEquals(1_500, ok("1500.5", Currency.LBP, RoundingMode.HALF_EVEN))
    }

    @Test
    fun `UNNECESSARY rejects extra decimals but allows trailing zeros`() {
        assertEquals(MoneyParseError.TOO_MANY_DECIMALS, fails("12.345", Currency.USD, RoundingMode.UNNECESSARY))
        assertEquals(MoneyParseError.TOO_MANY_DECIMALS, fails("1500.5", Currency.LBP, RoundingMode.UNNECESSARY))
        assertEquals(1_250, ok("12.500", Currency.USD, RoundingMode.UNNECESSARY))
        assertEquals(1_500, ok("1500.0", Currency.LBP, RoundingMode.UNNECESSARY))
    }

    @Test
    fun `amounts beyond Long range are rejected`() {
        assertEquals(MoneyParseError.OUT_OF_RANGE, fails("92233720368547758.08"))
        assertEquals(Long.MAX_VALUE, ok("92233720368547758.07", Currency.USD))
        assertEquals(MoneyParseError.OUT_OF_RANGE, fails("9".repeat(50)))
    }
}
