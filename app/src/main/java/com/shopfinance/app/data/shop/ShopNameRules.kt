package com.shopfinance.app.data.shop

import com.shopfinance.app.data.text.SingleLineText

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

    /** The name as it is stored; see [SingleLineText.normalize]. */
    fun normalize(raw: String): String = SingleLineText.normalize(raw)

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
