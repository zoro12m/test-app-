package com.shopfinance.domain.ledger

/**
 * What a ledger entry means for the shop's books. Each type moves the entry's (always positive)
 * amount into or out of up to three buckets, given as -1, 0 or +1:
 *
 * - [cash]: money physically in the shop (cash on hand).
 * - [receivable]: what customers owe the shop.
 * - [payable]: what the shop owes suppliers and lenders.
 * - [capital]: what the owner has put into the business, net of withdrawals.
 *
 * [wireName] is the value stored in Firestore.
 */
enum class EntryType(
    val wireName: String,
    val cash: Int,
    val receivable: Int,
    val payable: Int,
    val capital: Int,
    val requiresParty: Boolean,
) {
    /** Cash sale. */
    SALE("sale", cash = +1, receivable = 0, payable = 0, capital = 0, requiresParty = false),

    /** Cash paid out for rent, electricity, wages, etc. */
    EXPENSE("expense", cash = -1, receivable = 0, payable = 0, capital = 0, requiresParty = false),

    /** Goods sold to a customer on credit: no cash moves, the customer now owes more. */
    DEBT_GIVEN("debt_given", cash = 0, receivable = +1, payable = 0, capital = 0, requiresParty = true),

    /** A customer pays back part or all of what they owe. */
    DEBT_PAYMENT("debt_payment", cash = +1, receivable = -1, payable = 0, capital = 0, requiresParty = true),

    /** The owner puts money into the shop. */
    CAPITAL_IN("capital_in", cash = +1, receivable = 0, payable = 0, capital = +1, requiresParty = false),

    /** The owner takes money out of the shop. */
    CAPITAL_OUT("capital_out", cash = -1, receivable = 0, payable = 0, capital = -1, requiresParty = false),

    /** Goods or services received from a supplier on credit: no cash moves, the shop owes more. */
    LIABILITY_IN("liability_in", cash = 0, receivable = 0, payable = +1, capital = 0, requiresParty = true),

    /** A cash loan received from a lender: cash comes in and the shop owes it back. */
    LOAN_RECEIVED("loan_received", cash = +1, receivable = 0, payable = +1, capital = 0, requiresParty = true),

    /** The shop pays a supplier or lender. */
    LIABILITY_PAYMENT("liability_payment", cash = -1, receivable = 0, payable = -1, capital = 0, requiresParty = true),
    ;

    companion object {
        fun fromWireName(name: String): EntryType =
            entries.firstOrNull { it.wireName == name }
                ?: throw IllegalArgumentException("Unknown entry type: $name")
    }
}
