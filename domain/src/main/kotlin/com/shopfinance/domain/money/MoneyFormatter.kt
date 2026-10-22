package com.shopfinance.domain.money

enum class DigitStyle { LATIN, ARABIC_INDIC }

/**
 * Formats [Money] for display straight from minor units, with no floating point.
 *
 * Done by hand rather than with `NumberFormat` so the output is identical on the JVM and on
 * every Android version. Latin style uses `1,234.50`; Arabic-Indic style uses `١٬٢٣٤٫٥٠`.
 * Bidi isolation of the result inside RTL text is left to the UI.
 */
object MoneyFormatter {

    /** The amount only, e.g. `1,234.50` or `-75,000`. */
    fun formatAmount(money: Money, style: DigitStyle = DigitStyle.LATIN, grouping: Boolean = true): String {
        val digits = money.minor.toString().removePrefix("-") // safe for Long.MIN_VALUE
        val minorDigits = money.currency.minorDigits
        val padded = digits.padStart(minorDigits + 1, '0')
        val integerPart = padded.substring(0, padded.length - minorDigits)
        val fractionPart = padded.substring(padded.length - minorDigits)

        val groupSeparator = if (style == DigitStyle.LATIN) ',' else '٬'
        val decimalSeparator = if (style == DigitStyle.LATIN) '.' else '٫'

        val sb = StringBuilder()
        if (money.isNegative) sb.append('-')
        integerPart.forEachIndexed { index, c ->
            val remaining = integerPart.length - index
            if (grouping && index > 0 && remaining % 3 == 0) sb.append(groupSeparator)
            sb.append(c)
        }
        if (minorDigits > 0) sb.append(decimalSeparator).append(fractionPart)
        return localizeDigits(sb.toString(), style)
    }

    /** The amount followed by the currency symbol, e.g. `1,234.50 $` or `٧٥٬٠٠٠ ل.ل.`. */
    fun format(money: Money, style: DigitStyle = DigitStyle.LATIN): String =
        formatAmount(money, style) + ' ' + money.currency.symbol

    private fun localizeDigits(text: String, style: DigitStyle): String =
        if (style == DigitStyle.LATIN) {
            text
        } else {
            buildString(text.length) {
                for (c in text) append(if (c in '0'..'9') '٠' + (c - '0') else c)
            }
        }
}
