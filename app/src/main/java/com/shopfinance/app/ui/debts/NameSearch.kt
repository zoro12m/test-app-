package com.shopfinance.app.ui.debts

import com.shopfinance.app.data.text.SingleLineText

/**
 * Matching typed text against names, the forgiving way people expect: any part of the name,
 * any letter case, extra spaces ignored, and Arabic spelling variants treated alike (أحمد
 * finds احمد, هدى finds هدي, and short vowel marks are ignored).
 */
internal object NameSearch {

    /** True when [query] is empty or found anywhere in [name]. */
    fun matches(name: String, query: String): Boolean {
        val key = key(query)
        return key.isEmpty() || key in key(name)
    }

    /** [text] reduced to the form searches compare. */
    fun key(text: String): String = buildString {
        for (char in SingleLineText.normalize(text).lowercase()) {
            when (char) {
                'أ', 'إ', 'آ', 'ٱ' -> append('ا') // alef with hamza or madda -> alef
                'ى' -> append('ي') // alef maksura -> yeh
                'ة' -> append('ه') // teh marbuta -> heh
                in '\u064B'..'\u0652', '\u0640' -> Unit // short vowel marks and tatweel
                else -> append(char)
            }
        }
    }
}
