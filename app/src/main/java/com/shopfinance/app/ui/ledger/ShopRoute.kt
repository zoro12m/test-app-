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

    /** The list of people who owe the shop money. */
    data object Debtors : ShopRoute

    /** One person's debts and payments. */
    data class Debtor(val debtorId: String) : ShopRoute

    /** The add-debtor form. Form ids work as in [NewEntry]. */
    data class NewDebtor(val formId: String) : ShopRoute

    /** The add-debt form for the person [debtorId]. */
    data class NewDebt(val debtorId: String, val formId: String) : ShopRoute

    /** The record-payment form for the debt [debtId]. */
    data class NewPayment(val debtId: String, val formId: String) : ShopRoute

    companion object {
        /**
         * `dashboard|entries|new:sale:<formId>`, `dashboard|debtors|debtor:<id>|pay:<debtId>:<formId>`.
         * Form ids are UUIDs and document ids are letters and digits, so neither contains `|` or `:`.
         */
        fun encode(stack: List<ShopRoute>): String = stack.joinToString(SEPARATOR) { route ->
            when (route) {
                Dashboard -> DASHBOARD
                Entries -> ENTRIES
                is NewEntry -> "$NEW_ENTRY:${route.type.wireName}:${route.formId}"
                Debtors -> DEBTORS
                is Debtor -> "$DEBTOR:${route.debtorId}"
                is NewDebtor -> "$NEW_DEBTOR:${route.formId}"
                is NewDebt -> "$NEW_DEBT:${route.debtorId}:${route.formId}"
                is NewPayment -> "$NEW_PAYMENT:${route.debtId}:${route.formId}"
            }
        }

        /** The stack in [text]; always starts with [Dashboard], which only appears at the bottom. Unknown parts are dropped. */
        fun decode(text: String): List<ShopRoute> =
            listOf(Dashboard) + text.split(SEPARATOR).mapNotNull { parse(it) }.filter { it != Dashboard }

        private fun parse(part: String): ShopRoute? {
            when (part) {
                DASHBOARD -> return Dashboard
                ENTRIES -> return Entries
                DEBTORS -> return Debtors
            }
            val pieces = part.split(':')
            if (pieces.drop(1).any { !ID_PATTERN.matches(it) }) return null
            return when {
                pieces.size == 3 && pieces[0] == NEW_ENTRY ->
                    LedgerInputRules.SUPPORTED_TYPES.firstOrNull { it.wireName == pieces[1] }?.let { NewEntry(it, pieces[2]) }
                pieces.size == 2 && pieces[0] == DEBTOR -> Debtor(pieces[1])
                pieces.size == 2 && pieces[0] == NEW_DEBTOR -> NewDebtor(pieces[1])
                pieces.size == 3 && pieces[0] == NEW_DEBT -> NewDebt(pieces[1], pieces[2])
                pieces.size == 3 && pieces[0] == NEW_PAYMENT -> NewPayment(pieces[1], pieces[2])
                else -> null
            }
        }

        val INITIAL: String = DASHBOARD

        private val ID_PATTERN = Regex("[A-Za-z0-9_-]+")

        private const val SEPARATOR = "|"
        private const val DASHBOARD = "dashboard"
        private const val ENTRIES = "entries"
        private const val NEW_ENTRY = "new"
        private const val DEBTORS = "debtors"
        private const val DEBTOR = "debtor"
        private const val NEW_DEBTOR = "new-debtor"
        private const val NEW_DEBT = "new-debt"
        private const val NEW_PAYMENT = "pay"
    }
}
