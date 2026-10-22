package com.shopfinance.app.data.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopNameRulesTest {

    @Test
    fun normalizeTrimsBothEnds() {
        assertEquals("متجر أبو علي", ShopNameRules.normalize("  متجر أبو علي \n"))
    }

    @Test
    fun normalizeCollapsesInternalWhitespaceRunsToOneSpace() {
        assertEquals("Abu Ali Store", ShopNameRules.normalize("Abu   Ali\t\tStore"))
        assertEquals("Abu Ali", ShopNameRules.normalize("Abu\n\u00A0 Ali"))
    }

    @Test
    fun normalizeTrimsControlCharactersThatKotlinDoesNotCallWhitespace() {
        // The security rules' trim() strips everything up to U+0020; Kotlin's trim() keeps these.
        assertEquals("abc", ShopNameRules.normalize("\u0001abc\u0007"))
        assertEquals("abc", ShopNameRules.normalize("\u0000\u001Babc\u0008"))
        assertNull(ShopNameRules.validate("\u0001abc"))
        assertEquals(ShopNameError.EMPTY, ShopNameRules.validate("\u0001\u0002"))
    }

    @Test
    fun normalizeTurnsInternalControlCharactersIntoOneSpace() {
        assertEquals("a b", ShopNameRules.normalize("a\u0001b"))
        assertEquals("a b", ShopNameRules.normalize("a \u0007\tb"))
    }

    @Test
    fun normalizeKeepsAnAlreadyCleanNameAsIs() {
        val name = "متجر النور"
        assertEquals(name, ShopNameRules.normalize(name))
        assertEquals(name, ShopNameRules.normalize(ShopNameRules.normalize(name)))
    }

    @Test
    fun normalizeOfBlankIsEmpty() {
        assertEquals("", ShopNameRules.normalize(" \t\n "))
    }

    @Test
    fun emptyAndBlankNamesAreRejected() {
        assertEquals(ShopNameError.EMPTY, ShopNameRules.validate(""))
        assertEquals(ShopNameError.EMPTY, ShopNameRules.validate("   "))
        assertEquals(ShopNameError.EMPTY, ShopNameRules.validate("\n\t"))
    }

    @Test
    fun oneCharacterIsEnough() {
        assertNull(ShopNameRules.validate("م"))
    }

    @Test
    fun maxLengthIsSixty() {
        assertEquals(60, ShopNameRules.MAX_LENGTH)
    }

    @Test
    fun nameOfExactlyMaxLengthIsAccepted() {
        assertNull(ShopNameRules.validate("م".repeat(ShopNameRules.MAX_LENGTH)))
    }

    @Test
    fun nameOneOverMaxLengthIsTooLong() {
        assertEquals(ShopNameError.TOO_LONG, ShopNameRules.validate("a".repeat(ShopNameRules.MAX_LENGTH + 1)))
    }

    @Test
    fun lengthIsCheckedAfterNormalizing() {
        // 60 letters plus surrounding and doubled spaces: fine once normalized.
        val padded = "   " + "a".repeat(30) + "   " + "b".repeat(29) + "   "
        assertEquals(60, ShopNameRules.normalize(padded).length)
        assertNull(ShopNameRules.validate(padded))
    }

    @Test
    fun everyValidNameNormalizesToSomethingTheSecurityRulesAccept() {
        // firestore.rules: a string equal to its own trim, 1..60 characters long. The rules'
        // trim() strips every character up to U+0020, so both trims must leave it unchanged.
        val samples = listOf(" متجر ", "a  b", "x".repeat(60), "\tدكان\u00A0الحي\n", "\u0001متجر\u001B")
        for (sample in samples) {
            assertNull(sample, ShopNameRules.validate(sample))
            val stored = ShopNameRules.normalize(sample)
            assertEquals(sample, stored.trim { it <= ' ' }, stored)
            assertEquals(sample, stored.trim(), stored)
            assertTrue(sample, stored.length in 1..ShopNameRules.MAX_LENGTH)
        }
    }
}
