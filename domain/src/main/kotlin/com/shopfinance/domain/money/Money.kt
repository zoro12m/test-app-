package com.shopfinance.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An exact amount of money: a whole number of minor units (cents for USD, pounds for LBP)
 * plus its currency.
 *
 * All arithmetic is exact: it uses [Math.addExact] and friends, so an overflow throws
 * [ArithmeticException] instead of silently wrapping. Combining two currencies throws
 * [CurrencyMismatchException].
 */
data class Money(val minor: Long, val currency: Currency) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(minor, other.minor), currency)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(minor), currency)

    operator fun times(factor: Long): Money = Money(Math.multiplyExact(minor, factor), currency)

    operator fun times(factor: Int): Money = times(factor.toLong())

    fun abs(): Money = if (minor < 0) -this else this

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    /** The amount in major units (e.g. 12.50 for 1250 cents), exact. */
    fun toMajor(): BigDecimal = BigDecimal.valueOf(minor, currency.minorDigits)

    /**
     * Splits this amount into [parts] amounts that add up exactly to it.
     * The remainder is spread one minor unit at a time over the first parts,
     * so 10.00 USD in 3 parts is 3.34, 3.33, 3.33.
     */
    fun split(parts: Int): List<Money> {
        require(parts > 0) { "parts must be positive, was $parts" }
        val base = minor / parts
        val remainder = minor % parts // same sign as minor
        val step = if (remainder < 0) -1L else 1L
        val extra = kotlin.math.abs(remainder)
        return List(parts) { index -> Money(if (index < extra) base + step else base, currency) }
    }

    /**
     * Converts to another currency at an explicit, owner-supplied [rate]
     * (units of [target] per one unit of this currency, in major units).
     * There is no implicit conversion anywhere else in the domain.
     */
    fun convert(target: Currency, rate: BigDecimal, rounding: RoundingMode = RoundingMode.HALF_UP): Money {
        require(rate.signum() > 0) { "Exchange rate must be positive, was $rate" }
        return ofMajor(toMajor().multiply(rate), target, rounding)
    }

    fun requireSameCurrency(other: Money) {
        if (currency != other.currency) throw CurrencyMismatchException(currency, other.currency)
    }

    override fun toString(): String = "${toMajor().toPlainString()} ${currency.code}"

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)

        fun ofMinor(minor: Long, currency: Currency): Money = Money(minor, currency)

        /**
         * Builds money from a major-unit amount, rounding to the currency's minor unit with
         * [rounding]. Pass [RoundingMode.UNNECESSARY] to reject amounts with too many decimals.
         *
         * @throws ArithmeticException if rounding is needed but not allowed, or the result
         *   does not fit in a [Long].
         */
        fun ofMajor(amount: BigDecimal, currency: Currency, rounding: RoundingMode = RoundingMode.HALF_UP): Money {
            val scaled = amount.setScale(currency.minorDigits, rounding)
            return Money(scaled.unscaledValue().longValueExact(), currency)
        }

        fun ofMajor(amount: String, currency: Currency, rounding: RoundingMode = RoundingMode.HALF_UP): Money =
            ofMajor(BigDecimal(amount), currency, rounding)
    }
}
