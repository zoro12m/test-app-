package com.shopfinance.app.data.debts

import com.shopfinance.domain.debts.Debt
import com.shopfinance.domain.debts.DebtPayment
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DebtMapperTest {

    private val createdAt = Instant.parse("2026-10-01T10:00:02Z")

    /** Stands in for Firestore's server timestamp sentinel. */
    private val serverTimestamp = Any()

    private fun usd(minor: Long) = Money(minor, Currency.USD)

    /** Documents as the parse functions receive them: integers as Long, timestamps as Instant. */
    private fun storedDebtor(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "name" to "رامي",
        "phone" to "03 123 456",
        "note" to "",
        "createdBy" to "alice",
        "createdAt" to createdAt,
        "schemaVersion" to 1L,
    ) + overrides

    private fun storedDebt(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "debtorId" to "rami",
        "amountMinor" to 10_000L,
        "currency" to "USD",
        "debtDate" to "2026-10-01",
        "note" to "",
        "createdBy" to "alice",
        "createdAt" to createdAt,
        "schemaVersion" to 1L,
    ) + overrides

    private fun storedPayment(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "debtId" to "d1",
        "amountMinor" to 3_000L,
        "paymentDate" to "2026-10-05",
        "note" to "",
        "createdBy" to "alice",
        "createdAt" to createdAt,
        "schemaVersion" to 1L,
    ) + overrides

    @Test
    fun parsesADebtor() {
        assertEquals(
            DebtorRecord("rami", "رامي", "03 123 456", "", createdAt, pendingSync = true),
            parseDebtor("rami", storedDebtor(), pendingSync = true),
        )
        assertNull(parseDebtor("rami", storedDebtor("phone" to null), pendingSync = false)?.phone)
        assertNull(parseDebtor("rami", storedDebtor() - "phone", pendingSync = false)?.phone)
    }

    @Test
    fun skipsBrokenDebtors() {
        assertNull(parseDebtor("rami", storedDebtor("name" to "  "), pendingSync = false))
        assertNull(parseDebtor("rami", storedDebtor("createdAt" to null), pendingSync = false))
        assertNull(parseDebtor("rami", storedDebtor("deletedAt" to createdAt), pendingSync = false))
        assertNull(parseDebtor("", storedDebtor(), pendingSync = false))
    }

    @Test
    fun parsesUsdAndLbpDebts() {
        assertEquals(
            DebtRecord(Debt("d1", "rami", usd(10_000), LocalDate.of(2026, 10, 1)), createdAt, pendingSync = false),
            parseDebt("d1", storedDebt(), pendingSync = false),
        )
        assertEquals(
            Money(500_000, Currency.LBP),
            parseDebt("d2", storedDebt("currency" to "LBP", "amountMinor" to 500_000L), pendingSync = false)?.debt?.amount,
        )
    }

    @Test
    fun skipsDeletedAndBrokenDebts() {
        for (broken in listOf(
            storedDebt("deletedAt" to createdAt, "deletedBy" to "alice"),
            storedDebt("currency" to "EUR"),
            storedDebt("amountMinor" to 0L),
            storedDebt("amountMinor" to 12.5),
            storedDebt("debtDate" to "2026-10-1"),
            storedDebt("debtDate" to "2026-02-30"),
            storedDebt("debtDate" to createdAt),
            storedDebt("debtorId" to null),
            storedDebt("createdAt" to null),
        )) {
            assertNull(broken.toString(), parseDebt("d1", broken, pendingSync = false))
        }
    }

    @Test
    fun skipsDeletedAndBrokenPayments() {
        assertEquals(3_000L, parsePayment("p1", storedPayment(), pendingSync = false)?.amountMinor)
        assertNull(parsePayment("p1", storedPayment("deletedAt" to createdAt), pendingSync = false))
        assertNull(parsePayment("p1", storedPayment("amountMinor" to -1L), pendingSync = false))
        assertNull(parsePayment("p1", storedPayment("paymentDate" to null), pendingSync = false))
        assertNull(parsePayment("p1", storedPayment("debtId" to ""), pendingSync = false))
    }

    @Test
    fun paymentsTakeTheirDebtsCurrencyAndDropWhenTheDebtIsGone() {
        val usdDebt = parseDebt("d1", storedDebt(), pendingSync = false)!!
        val lbpDebt = parseDebt("d2", storedDebt("currency" to "LBP", "amountMinor" to 500_000L), pendingSync = false)!!
        val payments = listOf(
            parsePayment("p1", storedPayment(), pendingSync = false)!!,
            parsePayment("p2", storedPayment("debtId" to "d2", "amountMinor" to 150_000L), pendingSync = false)!!,
            // Its debt was deleted, so the payment is hidden with it.
            parsePayment("p3", storedPayment("debtId" to "deleted-debt"), pendingSync = false)!!,
        )

        val snapshot = buildDebtsSnapshot(emptyList(), listOf(usdDebt, lbpDebt), payments, fromCache = false)

        assertEquals(
            setOf(
                DebtPayment("p1", "d1", usd(3_000), LocalDate.of(2026, 10, 5)),
                DebtPayment("p2", "d2", Money(150_000, Currency.LBP), LocalDate.of(2026, 10, 5)),
            ),
            snapshot.payments.map { it.payment }.toSet(),
        )
        assertEquals(
            mapOf("d1" to usd(7_000), "d2" to Money(350_000, Currency.LBP)),
            snapshot.accounts().associate { it.debt.id to it.remaining },
        )
    }

    @Test
    fun theSnapshotSortsDebtorsByNameAndPaymentsNewestTypedFirst() {
        val later = createdAt.plusSeconds(60)
        val debtors = listOf(
            parseDebtor("k", storedDebtor("name" to "Karim"), pendingSync = false)!!,
            parseDebtor("h", storedDebtor("name" to "hiba"), pendingSync = false)!!,
        )
        val debt = parseDebt("d1", storedDebt(), pendingSync = false)!!
        val payments = listOf(
            parsePayment("p1", storedPayment(), pendingSync = false)!!,
            parsePayment("p2", storedPayment("createdAt" to later), pendingSync = true)!!,
        )

        val snapshot = buildDebtsSnapshot(debtors, listOf(debt), payments, fromCache = true)

        assertEquals(listOf("h", "k"), snapshot.debtors.map { it.id })
        assertEquals(listOf("p2", "p1"), snapshot.payments.map { it.payment.id })
        assertEquals(1, snapshot.pendingCount)
    }

    @Test
    fun writesADebtorWithOnlyTheKeysTheRulesAccept() {
        val fields = newDebtorFields("alice", NewDebtor("  رامي ", "٠٣ ١٢٣", " زبون "), serverTimestamp)

        assertEquals(
            mapOf(
                "name" to "رامي",
                "phone" to "03 123",
                "note" to "زبون",
                "createdBy" to "alice",
                "createdAt" to serverTimestamp,
                "schemaVersion" to 1L,
            ),
            fields,
        )
        assertFalse("phone" in newDebtorFields("alice", NewDebtor("رامي", "  ", ""), serverTimestamp))
    }

    @Test
    fun writesADebtWithItsDayAsText() {
        val fields = newDebtFields(
            "alice",
            NewDebt("rami", Money(500_000, Currency.LBP), LocalDate.of(2026, 10, 1), ""),
            serverTimestamp,
        )

        assertEquals(
            mapOf(
                "debtorId" to "rami",
                "amountMinor" to 500_000L,
                "currency" to "LBP",
                "debtDate" to "2026-10-01",
                "note" to "",
                "createdBy" to "alice",
                "createdAt" to serverTimestamp,
                "schemaVersion" to 1L,
            ),
            fields,
        )
    }

    @Test
    fun writesAPaymentWithoutACurrency() {
        val fields = newPaymentFields("alice", "d1", NewPayment(usd(3_000), LocalDate.of(2026, 10, 5), ""), serverTimestamp)

        assertEquals(
            mapOf(
                "debtId" to "d1",
                "amountMinor" to 3_000L,
                "paymentDate" to "2026-10-05",
                "note" to "",
                "createdBy" to "alice",
                "createdAt" to serverTimestamp,
                "schemaVersion" to 1L,
            ),
            fields,
        )
    }

    @Test
    fun aDeleteSetsOnlyTheDeleteMark() {
        val fields = deleteFields("alice", serverTimestamp)

        assertEquals(setOf("deletedAt", "deletedBy"), fields.keys)
        assertSame(serverTimestamp, fields["deletedAt"])
        assertEquals("alice", fields["deletedBy"])
    }
}
