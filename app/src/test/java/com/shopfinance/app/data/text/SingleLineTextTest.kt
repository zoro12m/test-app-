package com.shopfinance.app.data.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SingleLineTextTest {

    @Test
    fun trimsAndCollapsesInternalRuns() {
        assertEquals("كهرباء شهر تشرين", SingleLineText.normalize("  كهرباء   شهر\tتشرين \n"))
    }

    @Test
    fun lineBreaksAndNoBreakSpacesBecomeOneSpace() {
        assertEquals("a b c", SingleLineText.normalize("a\r\n b c"))
    }

    @Test
    fun controlCharactersCountAsSeparators() {
        // The rules' trim() strips everything up to U+0020; isWhitespace() alone would keep these.
        assertEquals("a b", SingleLineText.normalize("\u0001a\u0002\u0003b\u001B"))
        assertEquals("", SingleLineText.normalize("\u0000\u0007\u001F"))
    }

    @Test
    fun emptyAndBlankTextBecomeEmpty() {
        assertEquals("", SingleLineText.normalize(""))
        assertEquals("", SingleLineText.normalize(" \t\n "))
    }

    @Test
    fun ordinaryTextIsUnchanged() {
        assertEquals("فاتورة رقم 12", SingleLineText.normalize("فاتورة رقم 12"))
    }

    @Test
    fun normalizingTwiceChangesNothing() {
        val once = SingleLineText.normalize(" \u0001 a \n\n b  ")
        assertEquals(once, SingleLineText.normalize(once))
    }
}
