package com.shopfinance.domain.ledger

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerEntryTest {

    private val now = Instant.parse("2026-10-22T10:00:00Z")
    private val tenDollars = Money(1_000, Currency.USD)

    @Test
    fun `amount must be positive`() {
        assertFailsWith<IllegalArgumentException> { LedgerEntry("1", EntryType.SALE, Money(0, Currency.USD), now) }
        assertFailsWith<IllegalArgumentException> { LedgerEntry("1", EntryType.SALE, Money(-1, Currency.USD), now) }
    }

    @Test
    fun `id must not be blank`() {
        assertFailsWith<IllegalArgumentException> { LedgerEntry(" ", EntryType.SALE, tenDollars, now) }
    }

    @Test
    fun `debt and liability entries need a party`() {
        val needParty = EntryType.entries.filter { it.requiresParty }
        assertEquals(
            setOf(EntryType.DEBT_GIVEN, EntryType.DEBT_PAYMENT, EntryType.LIABILITY_IN, EntryType.LOAN_RECEIVED, EntryType.LIABILITY_PAYMENT),
            needParty.toSet(),
        )
        for (type in needParty) {
            assertFailsWith<IllegalArgumentException>(type.name) { LedgerEntry("1", type, tenDollars, now) }
            assertFailsWith<IllegalArgumentException>(type.name) { LedgerEntry("1", type, tenDollars, now, partyId = "") }
            LedgerEntry("1", type, tenDollars, now, partyId = "p1")
        }
        LedgerEntry("1", EntryType.SALE, tenDollars, now)
    }

    @Test
    fun `void keeps the entry and records why`() {
        val entry = LedgerEntry("1", EntryType.SALE, tenDollars, now)
        assertFalse(entry.isVoided)
        val voided = entry.void(now.plusSeconds(60), "  duplicate  ")
        assertTrue(voided.isVoided)
        assertEquals(VoidInfo(now.plusSeconds(60), "duplicate"), voided.voided)
        assertEquals(entry.amount, voided.amount)
        assertEquals(entry.id, voided.id)
    }

    @Test
    fun `void needs a reason and happens once`() {
        val entry = LedgerEntry("1", EntryType.SALE, tenDollars, now)
        assertFailsWith<IllegalArgumentException> { entry.void(now, " ") }
        val voided = entry.void(now, "mistake")
        assertFailsWith<IllegalStateException> { voided.void(now, "again") }
        assertFailsWith<IllegalArgumentException> { entry.copy(voided = VoidInfo(now, "")) }
    }

    @Test
    fun `correction needs a fresh, live replacement`() {
        val entry = LedgerEntry("1", EntryType.SALE, tenDollars, now)
        assertFailsWith<IllegalArgumentException> { entry.correctWith(entry.copy(amount = Money(900, Currency.USD)), now, "fix") }
        val voidedReplacement = LedgerEntry("2", EntryType.SALE, tenDollars, now).void(now, "x")
        assertFailsWith<IllegalArgumentException> { entry.correctWith(voidedReplacement, now, "fix") }
    }

    @Test
    fun `wire names round trip`() {
        for (type in EntryType.entries) assertEquals(type, EntryType.fromWireName(type.wireName))
        assertEquals(EntryType.DEBT_GIVEN, EntryType.fromWireName("debt_given"))
        assertFailsWith<IllegalArgumentException> { EntryType.fromWireName("refund") }
    }

    @Test
    fun `every type has a consistent effect`() {
        for (type in EntryType.entries) {
            val effects = listOf(type.cash, type.receivable, type.payable, type.capital)
            assertTrue(effects.all { it in -1..1 }, type.name)
            assertTrue(effects.any { it != 0 }, "${type.name} moves nothing")
        }
    }
}
