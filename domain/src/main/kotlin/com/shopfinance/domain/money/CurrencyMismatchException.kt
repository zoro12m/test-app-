package com.shopfinance.domain.money

/** Thrown when two amounts in different currencies are combined. Conversion is never silent. */
class CurrencyMismatchException(
    val expected: Currency,
    val actual: Currency,
) : IllegalArgumentException("Currency mismatch: expected ${expected.code}, got ${actual.code}")
