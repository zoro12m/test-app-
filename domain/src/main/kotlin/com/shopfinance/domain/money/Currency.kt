package com.shopfinance.domain.money

/**
 * Currencies the shop can record amounts in.
 *
 * [minorDigits] is how many decimal places the smallest stored unit represents:
 * USD is stored in cents (2), LBP has no minor unit in practice (0).
 */
enum class Currency(val code: String, val minorDigits: Int, val symbol: String) {
    USD("USD", 2, "$"),
    LBP("LBP", 0, "ل.ل."),
    ;

    companion object {
        /** Looks up a currency by its ISO 4217 code, ignoring case. */
        fun fromCode(code: String): Currency =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Unsupported currency: $code")
    }
}
