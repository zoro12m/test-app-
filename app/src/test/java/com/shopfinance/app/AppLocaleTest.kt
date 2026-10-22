package com.shopfinance.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
    @Test
    fun defaultLanguageIsArabic() {
        assertEquals("ar", AppLocale.DEFAULT_LANGUAGE)
    }
}
