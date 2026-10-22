package com.shopfinance.app.data.shop

/** Why a shop name was rejected by [ShopNameRules.validate]. */
enum class ShopNameError {
    /** Empty, or nothing but whitespace. */
    EMPTY,

    /** Longer than [ShopNameRules.MAX_LENGTH] characters after [ShopNameRules.normalize]. */
    TOO_LONG,
}

/**
 * Shop name rules, shared by the setup screen and the repository. They agree with
 * firestore.rules, which only accept a name that is equal to its own trim and
 * 1..[MAX_LENGTH] characters long.
 */
object ShopNameRules {
    const val MAX_LENGTH = 60

    /**
     * The name as it is stored: trimmed, with every internal run of separators (spaces,
     * tabs, line breaks, no-break spaces, control characters) collapsed to one plain space.
     * See [isSeparator] for why control characters count.
     */
    fun normalize(raw: String): String {
        val result = StringBuilder(raw.length)
        var pendingSpace = false
        for (char in raw.trim { isSeparator(it) }) {
            if (isSeparator(char)) {
                pendingSpace = true
            } else {
                if (pendingSpace) result.append(' ')
                pendingSpace = false
                result.append(char)
            }
        }
        return result.toString()
    }

    /**
     * Kotlin whitespace, plus every character up to U+0020. The security rules' trim()
     * strips all of U+0000..U+0020, but Kotlin's isWhitespace() is false for most control
     * characters (U+0000..U+0008, U+000E..U+001B). Without this, a pasted "\u0001Shop" would
     * pass [validate] and then be refused by the server.
     */
    private fun isSeparator(char: Char): Boolean = char <= ' ' || char.isWhitespace()

    /**
     * Checks [raw] after [normalize]. Returns null when it is a valid name. The length is
     * counted in UTF-16 units, which is never less than the character count the security
     * rules use, so a name accepted here is never refused by the server for its length.
     */
    fun validate(raw: String): ShopNameError? {
        val name = normalize(raw)
        return when {
            name.isEmpty() -> ShopNameError.EMPTY
            name.length > MAX_LENGTH -> ShopNameError.TOO_LONG
            else -> null
        }
    }
}
