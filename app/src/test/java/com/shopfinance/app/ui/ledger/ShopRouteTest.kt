package com.shopfinance.app.ui.ledger

import com.shopfinance.domain.ledger.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopRouteTest {

    private val formId = "0b6f3c1e-2a4d-4f3b-9a51-7c2d8e9f1a20"

    @Test
    fun theInitialStackIsTheDashboard() {
        assertEquals(listOf(ShopRoute.Dashboard), ShopRoute.decode(ShopRoute.INITIAL))
    }

    @Test
    fun aStackSurvivesEncoding() {
        val stack = listOf(ShopRoute.Dashboard, ShopRoute.Entries, ShopRoute.NewEntry(EntryType.EXPENSE, formId))
        assertEquals(stack, ShopRoute.decode(ShopRoute.encode(stack)))
    }

    @Test
    fun everyRecordableTypeSurvivesEncoding() {
        for (type in listOf(EntryType.SALE, EntryType.EXPENSE, EntryType.CAPITAL_IN, EntryType.CAPITAL_OUT)) {
            val stack = listOf(ShopRoute.Dashboard, ShopRoute.NewEntry(type, formId))
            assertEquals(stack, ShopRoute.decode(ShopRoute.encode(stack)))
        }
    }

    @Test
    fun theDashboardIsAlwaysAtTheBottomAndOnlyThere() {
        assertEquals(
            listOf(ShopRoute.Dashboard, ShopRoute.Entries),
            ShopRoute.decode("entries|dashboard"),
        )
        assertEquals(listOf(ShopRoute.Dashboard), ShopRoute.decode(""))
    }

    @Test
    fun unknownOrBrokenPartsAreDropped() {
        assertEquals(
            listOf(ShopRoute.Dashboard, ShopRoute.Entries),
            ShopRoute.decode("dashboard|settings|entries|new:debt_given:$formId|new:sale:|new:sale"),
        )
    }
}
