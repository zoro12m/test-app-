package com.shopfinance.app.data.shop

import com.shopfinance.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ShopMapperTest {

    /** Stands in for a Firestore Timestamp or the server timestamp sentinel. */
    private val timestamp = Any()

    /** A document as Firestore returns it: integers come back as Long. */
    private fun storedShop(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "name" to "متجر النور",
        "ownerUid" to "uid-1",
        "memberUids" to listOf("uid-1"),
        "currency" to "USD",
        "createdAt" to timestamp,
        "updatedAt" to timestamp,
        "schemaVersion" to 1L,
    ) + overrides

    @Test
    fun parsesAValidDocument() {
        assertEquals(
            Shop(id = "shop-1", name = "متجر النور", ownerUid = "uid-1", currency = Currency.USD),
            parseShop("shop-1", storedShop()),
        )
    }

    @Test
    fun timestampsAndSchemaVersionAreNotRequired() {
        val data = storedShop() - setOf("createdAt", "updatedAt", "schemaVersion")
        assertEquals("متجر النور", parseShop("shop-1", data)?.name)
    }

    @Test
    fun pendingServerTimestampsReadAsNullAreFine() {
        // A shop written offline has null timestamps in the local cache until the server answers.
        val data = storedShop("createdAt" to null, "updatedAt" to null)
        assertEquals("shop-1", parseShop("shop-1", data)?.id)
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        assertEquals("shop-1", parseShop("shop-1", storedShop("addedLater" to "x"))?.id)
    }

    @Test
    fun nameIsTrimmed() {
        assertEquals("متجر النور", parseShop("shop-1", storedShop("name" to "  متجر النور "))?.name)
    }

    @Test
    fun missingBlankOrNonStringNameIsSkipped() {
        assertNull(parseShop("shop-1", storedShop() - "name"))
        assertNull(parseShop("shop-1", storedShop("name" to null)))
        assertNull(parseShop("shop-1", storedShop("name" to "   ")))
        assertNull(parseShop("shop-1", storedShop("name" to 42L)))
    }

    @Test
    fun missingBlankOrNonStringOwnerIsSkipped() {
        assertNull(parseShop("shop-1", storedShop() - "ownerUid"))
        assertNull(parseShop("shop-1", storedShop("ownerUid" to "")))
        assertNull(parseShop("shop-1", storedShop("ownerUid" to listOf("uid-1"))))
    }

    @Test
    fun blankIdIsSkipped() {
        assertNull(parseShop(" ", storedShop()))
    }

    @Test
    fun lbpIsRejectedBecauseTheAppIsUsdOnly() {
        // The domain module still knows LBP, but shops are USD only for now.
        assertNull(parseShop("shop-1", storedShop("currency" to Currency.LBP.code)))
    }

    @Test
    fun unknownMissingOrNonStringCurrencyIsSkipped() {
        assertNull(parseShop("shop-1", storedShop("currency" to "EUR")))
        assertNull(parseShop("shop-1", storedShop() - "currency"))
        assertNull(parseShop("shop-1", storedShop("currency" to 840L)))
    }

    @Test
    fun currencyCodeMustMatchExactly() {
        // The security rules only accept exactly "USD", so nothing else is a real document.
        assertNull(parseShop("shop-1", storedShop("currency" to "usd")))
        assertNull(parseShop("shop-1", storedShop("currency" to " USD")))
    }

    @Test
    fun newShopFieldsHaveExactlyTheKeysTheRulesAccept() {
        val fields = newShopFields(ownerUid = "uid-1", name = "متجر", serverTimestamp = timestamp)
        assertEquals(
            setOf("name", "ownerUid", "memberUids", "currency", "createdAt", "updatedAt", "schemaVersion"),
            fields.keys,
        )
    }

    @Test
    fun newShopFieldsValues() {
        val fields = newShopFields(ownerUid = "uid-1", name = "متجر", serverTimestamp = timestamp)
        assertEquals("متجر", fields["name"])
        assertEquals("uid-1", fields["ownerUid"])
        assertEquals(listOf("uid-1"), fields["memberUids"])
        assertEquals("USD", fields["currency"])
        assertSame(timestamp, fields["createdAt"])
        assertSame(timestamp, fields["updatedAt"])
        assertEquals(1L, fields["schemaVersion"])
    }

    @Test
    fun aNewShopReadsBack() {
        val fields = newShopFields(ownerUid = "uid-1", name = "متجر", serverTimestamp = timestamp)
        assertEquals(
            Shop(id = "shop-9", name = "متجر", ownerUid = "uid-1", currency = Currency.USD),
            parseShop("shop-9", fields),
        )
    }

    @Test
    fun shopsAreSortedByNameThenId() {
        val b = Shop(id = "1", name = "b", ownerUid = "u", currency = Currency.USD)
        val a2 = Shop(id = "2", name = "a", ownerUid = "u", currency = Currency.USD)
        val a1 = Shop(id = "1", name = "a", ownerUid = "u", currency = Currency.USD)
        assertEquals(listOf(a1, a2, b), sortShops(listOf(b, a2, a1)))
    }
}
