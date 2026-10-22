package com.shopfinance.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app is Arabic-first: it runs in Arabic (and therefore RTL) regardless of the
 * device language. A language setting can replace [DEFAULT_LANGUAGE] later.
 */
object AppLocale {
    const val DEFAULT_LANGUAGE = "ar"

    fun wrap(base: Context, language: String = DEFAULT_LANGUAGE): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
