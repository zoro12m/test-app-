package com.shopfinance.app.data.ledger

/**
 * What an expense was for. [wireName] is the categoryId stored in Firestore; the list must
 * match expenseCategories() in firestore.rules.
 */
enum class ExpenseCategory(val wireName: String) {
    /** Stock bought for resale. */
    GOODS("goods"),
    RENT("rent"),

    /** Electricity, including the generator subscription. */
    ELECTRICITY("electricity"),
    SALARIES("salaries"),
    TRANSPORT("transport"),
    MAINTENANCE("maintenance"),
    OTHER("other"),
    ;

    companion object {
        fun fromWireName(name: String?): ExpenseCategory? = entries.firstOrNull { it.wireName == name }
    }
}
