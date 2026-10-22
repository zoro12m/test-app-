package com.shopfinance.app.data.ledger

import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.ledger.LedgerEntry
import com.shopfinance.domain.ledger.VoidInfo
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Date

class LedgerMapperTest {

    private val occurredAt = Instant.parse("2026-10-01T10:00:00Z")
    private val createdAt = Instant.parse("2026-10-01T10:00:02Z")

    /** Stands in for Firestore's server timestamp sentinel. */
    private val serverTimestamp = Any()

    /** A document as parseEntry receives it: integers as Long, timestamps already as Instant. */
    private fun storedEntry(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "type" to "sale",
        "amountMinor" to 1_250L,
        "currency" to "USD",
        "occurredAt" to occurredAt,
        "note" to "",
        "createdBy" to "alice",
        "createdAt" to createdAt,
        "schemaVersion" to 1L,
    ) + overrides

    @Test
    fun parsesASale() {
        assertEquals(
            LedgerRecord(
                entry = LedgerEntry("e1", EntryType.SALE, Money(1_250, Currency.USD), occurredAt),
                createdBy = "alice",
                pendingSync = false,
            ),
            parseEntry("e1", storedEntry(), pendingSync = false),
        )
    }

    @Test
    fun parsesAnExpenseWithItsCategoryAndNote() {
        val record = parseEntry(
            "e2",
            storedEntry("type" to "expense", "categoryId" to "electricity", "note" to "اشتراك المولّد"),
            pendingSync = false,
        )
        assertEquals(EntryType.EXPENSE, record?.entry?.type)
        assertEquals("electricity", record?.entry?.categoryId)
        assertEquals("اشتراك المولّد", record?.entry?.note)
    }

    @Test
    fun pendingSyncIsPassedThrough() {
        assertTrue(parseEntry("e1", storedEntry(), pendingSync = true)!!.pendingSync)
    }

    @Test
    fun aVoidedEntryKeepsItsReasonAndTime() {
        val voidedAt = Instant.parse("2026-10-02T08:00:00Z")
        val record = parseEntry(
            "e1",
            storedEntry("voidedAt" to voidedAt, "voidReason" to "سُجّل مرتين", "voidedBy" to "alice"),
            pendingSync = false,
        )
        assertEquals(VoidInfo(voidedAt, "سُجّل مرتين"), record?.entry?.voided)
    }

    @Test
    fun aVoidWithoutItsTimeYetStillCountsAsVoided() {
        val record = parseEntry("e1", storedEntry("voidReason" to "خطأ"), pendingSync = true)
        assertEquals(VoidInfo(occurredAt, "خطأ"), record?.entry?.voided)
    }

    @Test
    fun aBlankVoidReasonIsNotAVoid() {
        assertFalse(parseEntry("e1", storedEntry("voidReason" to " "), pendingSync = false)!!.entry.isVoided)
    }

    @Test
    fun missingNoteReadsAsEmpty() {
        assertEquals("", parseEntry("e1", storedEntry() - "note", pendingSync = false)?.entry?.note)
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        assertEquals("e1", parseEntry("e1", storedEntry("addedLater" to true), pendingSync = false)?.entry?.id)
    }

    @Test
    fun unreadableDocumentsAreSkipped() {
        val broken = listOf(
            "blank id" to ("" to storedEntry()),
            "no type" to ("e1" to storedEntry() - "type"),
            "unknown type" to ("e1" to storedEntry("type" to "refund")),
            "amount not a whole number" to ("e1" to storedEntry("amountMinor" to 12.5)),
            "amount missing" to ("e1" to storedEntry() - "amountMinor"),
            "zero amount" to ("e1" to storedEntry("amountMinor" to 0L)),
            "negative amount" to ("e1" to storedEntry("amountMinor" to -5L)),
            "not USD" to ("e1" to storedEntry("currency" to "LBP")),
            "no currency" to ("e1" to storedEntry() - "currency"),
            "no date" to ("e1" to storedEntry() - "occurredAt"),
            "date not converted" to ("e1" to storedEntry("occurredAt" to Date.from(occurredAt))),
            "no author" to ("e1" to storedEntry() - "createdBy"),
            "credit entry without a party" to ("e1" to storedEntry("type" to "debt_given")),
        )
        for ((case, document) in broken) {
            assertNull(case, parseEntry(document.first, document.second, pendingSync = false))
        }
    }

    @Test
    fun newSaleHasExactlyTheKeysTheRulesAllow() {
        val fields = newEntryFields("alice", newEntry(), serverTimestamp)
        assertEquals(
            setOf("type", "amountMinor", "currency", "occurredAt", "note", "createdBy", "createdAt", "schemaVersion"),
            fields.keys,
        )
    }

    @Test
    fun newEntryFieldsHoldTheValuesInFirestoreTypes() {
        val fields = newEntryFields("alice", newEntry(), serverTimestamp)
        assertEquals("sale", fields["type"])
        assertEquals(1_250L, fields["amountMinor"])
        assertEquals("USD", fields["currency"])
        assertEquals(Date.from(occurredAt), fields["occurredAt"])
        assertEquals("alice", fields["createdBy"])
        assertSame(serverTimestamp, fields["createdAt"])
        assertEquals(1L, fields["schemaVersion"])
    }

    @Test
    fun newExpenseAddsItsCategory() {
        val fields = newEntryFields(
            "alice",
            newEntry(type = EntryType.EXPENSE, category = ExpenseCategory.TRANSPORT),
            serverTimestamp,
        )
        assertEquals("expense", fields["type"])
        assertEquals("transport", fields["categoryId"])
    }

    @Test
    fun theNoteIsStoredNormalized() {
        val fields = newEntryFields("alice", newEntry(note = "  دفعة\nأولى  "), serverTimestamp)
        assertEquals("دفعة أولى", fields["note"])
    }

    @Test
    fun aNewEntryReadsBackAsTheSameEntry() {
        val written = newEntryFields(
            "alice",
            newEntry(type = EntryType.EXPENSE, category = ExpenseCategory.RENT, note = "إيجار تشرين"),
            serverTimestamp,
        )
        // What Firestore hands back, after FirestoreLedgerRepository turns timestamps into Instants.
        val stored = written.mapValues { (_, value) ->
            when (value) {
                is Date -> value.toInstant()
                serverTimestamp -> createdAt
                else -> value
            }
        }
        val record = parseEntry("e9", stored, pendingSync = false)!!
        assertEquals(
            LedgerEntry(
                id = "e9",
                type = EntryType.EXPENSE,
                amount = Money(1_250, Currency.USD),
                occurredAt = occurredAt,
                categoryId = "rent",
                note = "إيجار تشرين",
            ),
            record.entry,
        )
        assertEquals("alice", record.createdBy)
    }

    @Test
    fun voidFieldsSetOnlyTheThreeVoidKeys() {
        val fields = voidFields("bob", "  مبلغ\tخاطئ ", serverTimestamp)
        assertEquals(setOf("voidedAt", "voidReason", "voidedBy"), fields.keys)
        assertSame(serverTimestamp, fields["voidedAt"])
        assertEquals("مبلغ خاطئ", fields["voidReason"])
        assertEquals("bob", fields["voidedBy"])
    }

    @Test
    fun recordsAreSortedNewestFirstThenById() {
        val early = Instant.parse("2026-10-01T08:00:00Z")
        val late = Instant.parse("2026-10-01T09:00:00Z")
        val records = listOf(
            record("a", early),
            record("c", late),
            record("b", early),
            record("d", late),
        )
        assertEquals(listOf("d", "c", "b", "a"), sortRecords(records).map { it.entry.id })
    }

    private fun newEntry(
        type: EntryType = EntryType.SALE,
        category: ExpenseCategory? = null,
        note: String = "",
    ) = NewEntry(type, Money(1_250, Currency.USD), occurredAt, category, note)

    private fun record(id: String, at: Instant) = LedgerRecord(
        entry = LedgerEntry(id, EntryType.SALE, Money(100, Currency.USD), at),
        createdBy = "alice",
        pendingSync = false,
    )
}
