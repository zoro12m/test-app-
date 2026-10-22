package com.shopfinance.domain.money

/**
 * Totals kept separately per currency. Amounts in different currencies are never added
 * together; the UI shows one line per currency.
 *
 * Immutable. Currencies whose total is zero are dropped, so two totals are equal when
 * every currency's total is equal.
 */
class MoneyTotals private constructor(private val byCurrency: Map<Currency, Long>) {

    /** Currencies with a non-zero total, in declaration order. */
    val currencies: List<Currency> get() = Currency.entries.filter { it in byCurrency }

    val isZero: Boolean get() = byCurrency.isEmpty()

    operator fun get(currency: Currency): Money = Money(byCurrency[currency] ?: 0L, currency)

    operator fun plus(money: Money): MoneyTotals {
        val updated = byCurrency.toMutableMap()
        val sum = Math.addExact(updated[money.currency] ?: 0L, money.minor)
        if (sum == 0L) updated.remove(money.currency) else updated[money.currency] = sum
        return MoneyTotals(updated)
    }

    operator fun minus(money: Money): MoneyTotals = plus(-money)

    operator fun plus(other: MoneyTotals): MoneyTotals =
        other.byCurrency.entries.fold(this) { acc, (currency, minor) -> acc + Money(minor, currency) }

    operator fun minus(other: MoneyTotals): MoneyTotals = plus(-other)

    operator fun unaryMinus(): MoneyTotals = MoneyTotals(byCurrency.mapValues { Math.negateExact(it.value) })

    /** Non-zero totals, one per currency. */
    fun toList(): List<Money> = currencies.map { get(it) }

    override fun equals(other: Any?): Boolean = other is MoneyTotals && other.byCurrency == byCurrency

    override fun hashCode(): Int = byCurrency.hashCode()

    override fun toString(): String = if (isZero) "MoneyTotals(0)" else "MoneyTotals(${toList().joinToString()})"

    companion object {
        val EMPTY = MoneyTotals(emptyMap())

        fun of(vararg amounts: Money): MoneyTotals = sum(amounts.asIterable())

        fun sum(amounts: Iterable<Money>): MoneyTotals = amounts.fold(EMPTY) { acc, money -> acc + money }
    }
}
