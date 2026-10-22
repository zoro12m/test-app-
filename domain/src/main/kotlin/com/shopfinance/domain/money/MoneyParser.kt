package com.shopfinance.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

enum class MoneyParseError {
    EMPTY,
    INVALID_FORMAT,
    NEGATIVE,
    TOO_MANY_DECIMALS,
    OUT_OF_RANGE,
}

sealed interface MoneyParseResult {
    data class Success(val money: Money) : MoneyParseResult
    data class Failure(val error: MoneyParseError) : MoneyParseResult
}

/**
 * Parses an amount typed by the user into [Money].
 *
 * Accepts Western (0-9), Arabic-Indic (٠-٩) and Eastern Arabic-Indic (۰-۹) digits.
 * The decimal separator is `.` or `٫`; `,`, `٬` and `،` are thousands separators and must
 * group exactly three digits, so an ambiguous input like `1,5` is rejected rather than
 * guessed. Spaces and bidi control marks are ignored. Amounts are always entered as
 * positive numbers; the direction comes from the entry type.
 */
object MoneyParser {

    private const val MAX_INPUT_LENGTH = 40

    private val GROUPING = setOf(',', '٬', '،')
    private val DECIMAL = setOf('.', '٫')
    private val MINUS = setOf('-', '−')
    private val IGNORED = setOf(
        ' ', ' ', ' ', ' ', // spaces
        '‎', '‏', '؜', // LRM, RLM, Arabic letter mark
        '‪', '‫', '‬', '‭', '‮', // embeddings and overrides
        '⁦', '⁧', '⁨', '⁩', // isolates
    )

    // Either plain digits or digits grouped in threes, then an optional fraction; or a bare fraction.
    private val FORMAT = Regex("""^(?:(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d*)?|\.\d+)$""")

    /**
     * @param rounding how to round extra decimal places to the currency's minor unit.
     *   [RoundingMode.UNNECESSARY] rejects them with [MoneyParseError.TOO_MANY_DECIMALS].
     */
    fun parse(input: String, currency: Currency, rounding: RoundingMode = RoundingMode.HALF_UP): MoneyParseResult {
        val normalized = normalize(input) ?: return MoneyParseResult.Failure(MoneyParseError.INVALID_FORMAT)
        if (normalized.isEmpty()) return MoneyParseResult.Failure(MoneyParseError.EMPTY)
        if (normalized.length > MAX_INPUT_LENGTH) return MoneyParseResult.Failure(MoneyParseError.OUT_OF_RANGE)
        if (normalized[0] == '-') {
            val rest = normalized.substring(1)
            return MoneyParseResult.Failure(
                if (FORMAT.matches(rest)) MoneyParseError.NEGATIVE else MoneyParseError.INVALID_FORMAT,
            )
        }
        if (!FORMAT.matches(normalized)) return MoneyParseResult.Failure(MoneyParseError.INVALID_FORMAT)

        val value = BigDecimal(normalized.replace(",", ""))
        if (value.scale() > currency.minorDigits && rounding == RoundingMode.UNNECESSARY) {
            // A typed "5.10" for USD has scale 2 and is fine; "5.123" is not.
            if (value.stripTrailingZeros().scale() > currency.minorDigits) {
                return MoneyParseResult.Failure(MoneyParseError.TOO_MANY_DECIMALS)
            }
        }
        return try {
            MoneyParseResult.Success(Money.ofMajor(value, currency, rounding))
        } catch (e: ArithmeticException) {
            MoneyParseResult.Failure(MoneyParseError.OUT_OF_RANGE)
        }
    }

    /** Returns ASCII digits with `,` grouping and `.` decimal, or null on a character we never accept. */
    private fun normalize(input: String): String? {
        val out = StringBuilder(input.length)
        for (c in input) {
            when {
                c in IGNORED -> Unit
                c in '0'..'9' -> out.append(c)
                c in '٠'..'٩' -> out.append('0' + (c - '٠'))
                c in '۰'..'۹' -> out.append('0' + (c - '۰'))
                c in GROUPING -> out.append(',')
                c in DECIMAL -> out.append('.')
                c in MINUS -> out.append('-')
                else -> return null
            }
        }
        return out.toString()
    }
}
