package com.shopfinance.domain.ledger

import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LedgerTest {

    private fun usd(minor: Long) = Money(minor, Currency.USD)
    private fun lbp(minor: Long) = Money(minor, Currency.LBP)
    private fun day(d: Int): Instant = Instant.parse("2026-10-%02dT09:00:00Z".format(d))

    private var nextId = 0
    private fun entry(type: EntryType, amount: Money, on: Int = 1, party: String? = null, category: String? = null) =
        LedgerEntry(
            id = "tx-${nextId++}",
            type = type,
            amount = amount,
            occurredAt = day(on),
            partyId = party,
            categoryId = category,
        )

    @Test
    fun `empty ledger has zero balances`() {
        val balances = Ledger(emptyList()).balances()
        assertEquals(Balances.EMPTY, balances)
        assertTrue(balances.netPosition.isZero)
    }

    @Test
    fun `each entry type moves the right buckets`() {
        val ledger = Ledger(
            listOf(
                entry(EntryType.CAPITAL_IN, usd(100_000)), // owner puts in 1,000.00
                entry(EntryType.SALE, usd(5_000)), // cash sale 50.00
                entry(EntryType.EXPENSE, usd(2_000)), // rent 20.00
                entry(EntryType.DEBT_GIVEN, usd(3_000), party = "c1"), // credit sale 30.00
                entry(EntryType.DEBT_PAYMENT, usd(1_000), party = "c1"), // customer pays 10.00
                entry(EntryType.LIABILITY_IN, usd(40_000), party = "s1"), // stock on credit 400.00
                entry(EntryType.LIABILITY_PAYMENT, usd(15_000), party = "s1"), // pay supplier 150.00
                entry(EntryType.LOAN_RECEIVED, usd(50_000), party = "bank"), // loan 500.00
                entry(EntryType.CAPITAL_OUT, usd(10_000)), // owner withdraws 100.00
            ),
        )
        val b = ledger.balances()
        // 1000 + 50 - 20 + 10 - 150 + 500 - 100 = 1290
        assertEquals(MoneyTotals.of(usd(129_000)), b.cashOnHand)
        // 30 - 10 = 20
        assertEquals(MoneyTotals.of(usd(2_000)), b.receivables)
        // 400 - 150 + 500 = 750
        assertEquals(MoneyTotals.of(usd(75_000)), b.payables)
        // 1000 - 100 = 900
        assertEquals(MoneyTotals.of(usd(90_000)), b.capital)
        // 1290 + 20 - 750 = 560
        assertEquals(MoneyTotals.of(usd(56_000)), b.netPosition)
    }

    @Test
    fun `currencies are never mixed`() {
        val ledger = Ledger(
            listOf(
                entry(EntryType.SALE, usd(1_000)),
                entry(EntryType.SALE, lbp(900_000)),
                entry(EntryType.EXPENSE, lbp(100_000)),
                entry(EntryType.DEBT_GIVEN, lbp(50_000), party = "c1"),
                entry(EntryType.DEBT_GIVEN, usd(500), party = "c1"),
            ),
        )
        val b = ledger.balances()
        assertEquals(usd(1_000), b.cashOnHand[Currency.USD])
        assertEquals(lbp(800_000), b.cashOnHand[Currency.LBP])
        assertEquals(MoneyTotals.of(usd(500), lbp(50_000)), b.receivables)
        assertEquals(MoneyTotals.of(usd(500), lbp(50_000)), ledger.partyBalance("c1"))
    }

    @Test
    fun `voided entries count for nothing`() {
        val sale = entry(EntryType.SALE, usd(5_000))
        val mistake = entry(EntryType.SALE, usd(50_000)).void(day(2), "typed an extra zero")
        val ledger = Ledger(listOf(sale, mistake))
        assertEquals(MoneyTotals.of(usd(5_000)), ledger.balances().cashOnHand)
        assertEquals(listOf(sale), ledger.active)
        assertEquals(2, ledger.entries.size)
    }

    @Test
    fun `a correction voids the original and adds the replacement`() {
        val original = entry(EntryType.EXPENSE, usd(20_000), category = "rent")
        val fixed = entry(EntryType.EXPENSE, usd(2_000), category = "rent")
        val correction = original.correctWith(fixed, day(3), "wrong amount")

        assertTrue(correction.voided.isVoided)
        assertEquals("wrong amount", correction.voided.voided?.reason)
        assertEquals(original.amount, correction.voided.amount) // kept for the audit trail

        val ledger = Ledger(listOf(correction.voided, correction.replacement))
        assertEquals(MoneyTotals.of(usd(-2_000)), ledger.balances().cashOnHand)
    }

    @Test
    fun `balances as of a moment ignore later entries`() {
        val ledger = Ledger(
            listOf(
                entry(EntryType.SALE, usd(100), on = 1),
                entry(EntryType.SALE, usd(200), on = 2),
                entry(EntryType.SALE, usd(400), on = 3),
            ),
        )
        assertEquals(MoneyTotals.of(usd(300)), ledger.balances(asOf = day(2)).cashOnHand)
        assertEquals(MoneyTotals.EMPTY, ledger.balances(asOf = day(1).minusSeconds(1)).cashOnHand)
    }

    @Test
    fun `cash flow over a half-open period`() {
        val ledger = Ledger(
            listOf(
                entry(EntryType.SALE, usd(1_000), on = 1),
                entry(EntryType.SALE, usd(2_000), on = 2),
                entry(EntryType.EXPENSE, usd(500), on = 2),
                entry(EntryType.DEBT_GIVEN, usd(9_999), on = 2, party = "c1"), // no cash
                entry(EntryType.DEBT_PAYMENT, usd(300), on = 3, party = "c1"),
                entry(EntryType.SALE, usd(7_000), on = 4), // excluded: until is exclusive
                entry(EntryType.SALE, lbp(50_000), on = 3),
            ),
        )
        val flow = ledger.cashFlow(day(2), day(4))
        assertEquals(MoneyTotals.of(usd(2_300), lbp(50_000)), flow.inflow)
        assertEquals(MoneyTotals.of(usd(500)), flow.outflow)
        assertEquals(MoneyTotals.of(usd(1_800), lbp(50_000)), flow.net)
        assertFailsWith<IllegalArgumentException> { ledger.cashFlow(day(4), day(4)) }
    }

    @Test
    fun `totals by type and expenses by category`() {
        val ledger = Ledger(
            listOf(
                entry(EntryType.EXPENSE, usd(1_000), category = "rent"),
                entry(EntryType.EXPENSE, usd(250), category = "power"),
                entry(EntryType.EXPENSE, usd(750), category = "rent"),
                entry(EntryType.EXPENSE, lbp(20_000)),
                entry(EntryType.SALE, usd(5_000)),
                entry(EntryType.SALE, usd(5_000)).void(day(1), "duplicate"),
            ),
        )
        val byType = ledger.totalsByType(day(1), day(2))
        assertEquals(MoneyTotals.of(usd(2_000), lbp(20_000)), byType[EntryType.EXPENSE])
        assertEquals(MoneyTotals.of(usd(5_000)), byType[EntryType.SALE])
        assertEquals(setOf(EntryType.EXPENSE, EntryType.SALE), byType.keys)

        val byCategory = ledger.expensesByCategory(day(1), day(2))
        assertEquals(MoneyTotals.of(usd(1_750)), byCategory["rent"])
        assertEquals(MoneyTotals.of(usd(250)), byCategory["power"])
        assertEquals(MoneyTotals.of(lbp(20_000)), byCategory[null])
    }

    @Test
    fun `party balances are positive when owed to us and negative when we owe`() {
        val ledger = Ledger(
            listOf(
                entry(EntryType.DEBT_GIVEN, usd(5_000), party = "customer"),
                entry(EntryType.DEBT_PAYMENT, usd(2_000), party = "customer"),
                entry(EntryType.LIABILITY_IN, usd(10_000), party = "supplier"),
                entry(EntryType.LIABILITY_PAYMENT, usd(4_000), party = "supplier"),
                entry(EntryType.DEBT_GIVEN, usd(700), party = "settled"),
                entry(EntryType.DEBT_PAYMENT, usd(700), party = "settled"),
            ),
        )
        val balances = ledger.partyBalances()
        assertEquals(MoneyTotals.of(usd(3_000)), balances["customer"])
        assertEquals(MoneyTotals.of(usd(-6_000)), balances["supplier"])
        assertEquals(setOf("customer", "supplier"), balances.keys) // settled is zero
        assertEquals(MoneyTotals.EMPTY, ledger.partyBalance("settled"))
        assertEquals(MoneyTotals.EMPTY, ledger.partyBalance("nobody"))
    }

    @Test
    fun `reconcile reports every cached balance that drifted`() {
        val ledger = Ledger(
            listOf(
                entry(EntryType.DEBT_GIVEN, usd(5_000), party = "a"),
                entry(EntryType.DEBT_GIVEN, lbp(100_000), party = "b"),
                entry(EntryType.DEBT_GIVEN, usd(1_000), party = "c"),
            ),
        )
        val cached = mapOf(
            "a" to MoneyTotals.of(usd(5_000)), // correct
            "b" to MoneyTotals.of(lbp(90_000)), // missed a write
            "d" to MoneyTotals.of(usd(200)), // no ledger entries at all
            // "c" missing from the cache
        )
        val drift = ledger.reconcile(cached)
        assertEquals(
            listOf(
                BalanceDrift("b", cached = lbp(90_000), actual = lbp(100_000)),
                BalanceDrift("c", cached = usd(0), actual = usd(1_000)),
                BalanceDrift("d", cached = usd(200), actual = usd(0)),
            ),
            drift,
        )
        assertEquals(lbp(-10_000), drift[0].difference)
        assertTrue(ledger.reconcile(ledger.partyBalances()).isEmpty())
    }

    @Test
    fun `statement lists a party's entries in time order, voids included`() {
        val later = entry(EntryType.DEBT_PAYMENT, usd(100), on = 5, party = "c1")
        val earlier = entry(EntryType.DEBT_GIVEN, usd(500), on = 2, party = "c1")
        val voided = entry(EntryType.DEBT_GIVEN, usd(900), on = 3, party = "c1").void(day(3), "wrong customer")
        val other = entry(EntryType.DEBT_GIVEN, usd(1), on = 1, party = "c2")
        val statement = Ledger(listOf(later, other, voided, earlier)).statement("c1")
        assertEquals(listOf(earlier, voided, later), statement)
    }

    @Test
    fun `duplicate entry ids are rejected`() {
        val e = entry(EntryType.SALE, usd(100))
        assertFailsWith<IllegalArgumentException> { Ledger(listOf(e, e.copy(amount = usd(200)))) }
    }

    @Test
    fun `balances overflow throws`() {
        val ledger = Ledger(listOf(entry(EntryType.SALE, usd(Long.MAX_VALUE)), entry(EntryType.SALE, usd(1))))
        assertFailsWith<ArithmeticException> { ledger.balances() }
    }
}
