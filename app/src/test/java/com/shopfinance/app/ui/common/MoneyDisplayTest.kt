package com.shopfinance.app.ui.common

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyDisplayTest {

    /** [text] inside an LTR isolate, with its plain space as the formatter's no-break space. */
    private fun isolated(text: String) = "\u2066${text.replace(' ', '\u00A0')}\u2069"

    @Test
    fun amountsUseWesternDigitsInsideAnLtrIsolate() {
        assertEquals(isolated("1,234.50 $"), displayMoney(Money(123_450, Currency.USD)))
        assertEquals(isolated("0.00 $"), displayMoney(Money(0, Currency.USD)))
        assertEquals(isolated("-40.00 $"), displayMoney(Money(-4_000, Currency.USD)))
    }

    @Test
    fun signedAmountsShowTheDirection() {
        val amount = Money(1_250, Currency.USD)
        assertEquals(isolated("+12.50 $"), displaySignedMoney(amount, direction = 1))
        assertEquals(isolated("-12.50 $"), displaySignedMoney(amount, direction = -1))
        assertEquals(isolated("12.50 $"), displaySignedMoney(amount, direction = 0))
    }

    @Test
    fun totalsShowOneAmountPerCurrency() {
        val usd = Money(2_500, Currency.USD)
        val lbp = Money(500_000, Currency.LBP)

        assertEquals("${isolated("25.00 $")} · ${displayMoney(lbp)}", displayTotals(MoneyTotals.of(lbp, usd)))
        assertEquals(isolated("25.00 $"), displayTotals(MoneyTotals.of(usd)))
        assertEquals("", displayTotals(MoneyTotals.EMPTY))
    }
}
