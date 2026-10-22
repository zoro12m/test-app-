package com.shopfinance.app.ui.ledger

import com.shopfinance.app.data.ledger.LedgerInputRules
import com.shopfinance.domain.ledger.EntryType

/**
 * The screens inside an open shop. The back stack is kept as one string (see [encode]) so it
 * survives rotation and process death through rememberSaveable without a navigation library.
 */
internal sealed interface ShopRoute {

    data object Dashboard : ShopRoute

    data object Entries : ShopRoute

    /** The add-entry form. [formId] is unique per opening, so each opening gets a fresh form. */
    data class NewEntry(val type: EntryType, val formId: String) : ShopRoute

    companion object {
        /** `dashboard|entries|new:sale:<formId>`. Form ids are UUIDs, which never contain `|` or `:`. */
        fun encode(stack: List<ShopRoute>): String = stack.joinToString(SEPARATOR) { route ->
            when (route) {
                Dashboard -> DASHBOARD
                Entries -> ENTRIES
                is NewEntry -> "$NEW_ENTRY:${route.type.wireName}:${route.formId}"
            }
        }

        /** The stack in [text]; always starts with [Dashboard], which only appears at the bottom. Unknown parts are dropped. */
        fun decode(text: String): List<ShopRoute> =
            listOf(Dashboard) + text.split(SEPARATOR).mapNotNull { parse(it) }.filter { it != Dashboard }

        private fun parse(part: String): ShopRoute? {
            if (part == DASHBOARD) return Dashboard
            if (part == ENTRIES) return Entries
            val pieces = part.split(':')
            if (pieces.size != 3 || pieces[0] != NEW_ENTRY || pieces[2].isEmpty()) return null
            val type = LedgerInputRules.SUPPORTED_TYPES.firstOrNull { it.wireName == pieces[1] } ?: return null
            return NewEntry(type, pieces[2])
        }

        val INITIAL: String = DASHBOARD

        private const val SEPARATOR = "|"
        private const val DASHBOARD = "dashboard"
        private const val ENTRIES = "entries"
        private const val NEW_ENTRY = "new"
    }
}
