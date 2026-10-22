package com.shopfinance.app.ui.common

import com.shopfinance.domain.money.DigitStyle
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyFormatter

/** Left-to-right isolate: keeps "1,234.50 $" in its own order inside Arabic text. */
private const val LTR_ISOLATE = '\u2066'
private const val POP_ISOLATE = '\u2069'

/**
 * [money] as the app shows it, e.g. `1,234.50 $`. Western digits, as on Lebanese receipts
 * and price tags, wrapped in a left-to-right isolate so the sign, digits and symbol keep
 * their order inside right-to-left text.
 */
internal fun displayMoney(money: Money): String =
    "$LTR_ISOLATE${MoneyFormatter.format(money, DigitStyle.LATIN)}$POP_ISOLATE"

/**
 * A positive [amount] with an explicit direction, e.g. `+12.50 $` for money in and
 * `-12.50 $` for money out. [direction] is +1, -1 or 0 (no sign).
 */
internal fun displaySignedMoney(amount: Money, direction: Int): String {
    val sign = when {
        direction > 0 -> "+"
        direction < 0 -> "-"
        else -> ""
    }
    return "$LTR_ISOLATE$sign${MoneyFormatter.format(amount.abs(), DigitStyle.LATIN)}$POP_ISOLATE"
}
