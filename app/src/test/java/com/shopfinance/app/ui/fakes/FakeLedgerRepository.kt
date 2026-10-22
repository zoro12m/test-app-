package com.shopfinance.app.ui.fakes

import com.shopfinance.app.data.ledger.LedgerRecord
import com.shopfinance.app.data.ledger.LedgerRejection
import com.shopfinance.app.data.ledger.LedgerRepository
import com.shopfinance.app.data.ledger.LedgerSnapshot
import com.shopfinance.app.data.ledger.NewEntry
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.ledger.LedgerEntry
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 * In-memory [LedgerRepository] with one stream per shop. Tests send snapshots with [push],
 * make the stream fail with [fail] and report server refusals with [reject]. The latest
 * event is replayed to new listeners, like a snapshot listener that answers from the cache.
 * Writes are only recorded; they do not change the stream.
 */
class FakeLedgerRepository : LedgerRepository {

    data class AddCall(val shopId: String, val createdBy: String, val entry: NewEntry)

    data class VoidCall(val shopId: String, val entryId: String, val voidedBy: String, val reason: String)

    private val streams = mutableMapOf<String, MutableSharedFlow<Event>>()
    private val rejectionEvents = MutableSharedFlow<LedgerRejection>(extraBufferCapacity = 16)

    /** The shop ids passed to [observeEntries], once per started listener. */
    val observeCalls = mutableListOf<String>()

    val addCalls = mutableListOf<AddCall>()
    val voidCalls = mutableListOf<VoidCall>()

    /** When set, [addEntry] throws it instead of recording anything. */
    var addError: RuntimeException? = null

    fun push(shopId: String, snapshot: LedgerSnapshot) {
        check(stream(shopId).tryEmit(Event.Snapshot(snapshot)))
    }

    fun fail(shopId: String, error: Throwable) {
        check(stream(shopId).tryEmit(Event.Failure(error)))
    }

    fun reject(rejection: LedgerRejection) {
        check(rejectionEvents.tryEmit(rejection))
    }

    override val rejections: Flow<LedgerRejection> = rejectionEvents.asSharedFlow()

    override fun observeEntries(shopId: String): Flow<LedgerSnapshot> = flow {
        observeCalls += shopId
        stream(shopId).collect { event ->
            when (event) {
                is Event.Snapshot -> emit(event.snapshot)
                is Event.Failure -> throw event.error
            }
        }
    }

    override fun addEntry(shopId: String, createdBy: String, entry: NewEntry): String {
        addError?.let { throw it }
        addCalls += AddCall(shopId, createdBy, entry)
        return "entry-${addCalls.size}"
    }

    override fun voidEntry(shopId: String, entryId: String, voidedBy: String, reason: String) {
        voidCalls += VoidCall(shopId, entryId, voidedBy, reason)
    }

    private fun stream(shopId: String): MutableSharedFlow<Event> = streams.getOrPut(shopId) {
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private sealed interface Event {
        data class Snapshot(val snapshot: LedgerSnapshot) : Event
        data class Failure(val error: Throwable) : Event
    }

    companion object {
        /** A stored record of [cents] USD, already synced unless [pendingSync]. */
        fun record(
            id: String,
            type: EntryType,
            cents: Long,
            occurredAt: Instant,
            categoryId: String? = null,
            pendingSync: Boolean = false,
        ): LedgerRecord = LedgerRecord(
            entry = LedgerEntry(
                id = id,
                type = type,
                amount = Money(cents, Currency.USD),
                occurredAt = occurredAt,
                categoryId = categoryId,
            ),
            createdBy = "alice",
            pendingSync = pendingSync,
        )
    }
}
