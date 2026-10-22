package com.shopfinance.app.data.text

/**
 * Normalization for short single-line text the app stores (shop names, notes, void reasons):
 * trimmed, with every internal run of separators (spaces, tabs, line breaks, no-break spaces,
 * control characters) collapsed to one plain space.
 *
 * The result is always equal to its own trim in the security rules, whose trim() strips every
 * character up to U+0020. Kotlin's isWhitespace() is false for most control characters
 * (U+0000..U+0008, U+000E..U+001B), so they count as separators here too; without that, a
 * pasted "\u0001Shop" would pass the app's checks and then be refused by the server.
 */
object SingleLineText {

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

    private fun isSeparator(char: Char): Boolean = char <= ' ' || char.isWhitespace()
}
