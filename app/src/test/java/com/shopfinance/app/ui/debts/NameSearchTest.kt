package com.shopfinance.app.ui.debts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSearchTest {

    @Test
    fun alefFormsAlefMaksuraAndTehMarbutaCompareAlike() {
        assertEquals(NameSearch.key("احمد اسامة"), NameSearch.key("أحمد إسامه"))
        assertEquals(NameSearch.key("ايات"), NameSearch.key("آيات"))
        assertEquals(NameSearch.key("هدي"), NameSearch.key("هدى"))
    }

    @Test
    fun vowelMarksAndTatweelAreIgnored() {
        assertEquals("محمد", NameSearch.key("مُحَمَّد"))
        assertEquals("محمد", NameSearch.key("محـــمد"))
    }

    @Test
    fun anyPartOfTheNameMatches() {
        assertTrue(NameSearch.matches("Rami Haddad", "hadd"))
        assertTrue(NameSearch.matches("أبو علي", "علي"))
        assertTrue(NameSearch.matches("أبو علي", ""))
        assertFalse(NameSearch.matches("أبو علي", "عمر"))
    }
}
