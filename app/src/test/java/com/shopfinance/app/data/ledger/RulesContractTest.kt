package com.shopfinance.app.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the app's ledger limits match firestore.rules, so the app never queues a write
 * the server will refuse. The rules themselves are tested against the emulator in
 * firestore-tests/.
 */
class RulesContractTest {

    /** firestore.rules at the repository root; unit tests run from the app module's folder. */
    private val rules: String by lazy {
        val start = File(System.getProperty("user.dir")).absoluteFile
        val file = generateSequence(start) { it.parentFile }
            .take(4)
            .map { File(it, "firestore.rules") }
            .firstOrNull { it.isFile }
            ?: error("firestore.rules not found above $start")
        file.readText()
    }

    /** The quoted strings returned by the rules function [name], e.g. `return ['a', 'b'];`. */
    private fun listFunction(name: String): List<String> {
        val body = Regex("""function $name\(\)\s*\{\s*return\s*\[([^\]]*)]""").find(rules)
            ?.groupValues?.get(1)
            ?: error("function $name() not found in firestore.rules")
        return Regex("""'([^']*)'""").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun expenseCategoriesMatch() {
        assertEquals(ExpenseCategory.entries.map { it.wireName }, listFunction("expenseCategories"))
    }

    @Test
    fun entryTypesMatch() {
        assertEquals(LedgerInputRules.SUPPORTED_TYPES.map { it.wireName }, listFunction("entryTypes"))
    }

    @Test
    fun limitsMatch() {
        assertTrue(rules.contains("data.amountMinor <= ${LedgerInputRules.MAX_AMOUNT_MINOR}"))
        assertTrue(rules.contains("data.note.size() <= ${LedgerInputRules.NOTE_MAX_LENGTH}"))
        assertTrue(rules.contains("reason.size() <= ${LedgerInputRules.VOID_REASON_MAX_LENGTH}"))
        assertTrue(rules.contains("data.schemaVersion == $ENTRY_SCHEMA_VERSION"))
    }
}
