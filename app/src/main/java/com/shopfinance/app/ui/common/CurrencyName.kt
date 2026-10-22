package com.shopfinance.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shopfinance.app.R
import com.shopfinance.domain.money.Currency

/**
 * The name of [currency] as the app shows it. The app only works in USD for now, so only
 * USD has a translated name; any other currency falls back to its ISO code.
 */
@Composable
internal fun currencyName(currency: Currency): String = when (currency) {
    Currency.USD -> stringResource(R.string.currency_usd)
    else -> currency.code
}
