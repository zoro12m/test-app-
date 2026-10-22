package com.shopfinance.app.ui.fakes

import com.shopfinance.app.data.shop.Shop
import com.shopfinance.app.data.shop.ShopRepository
import com.shopfinance.app.data.shop.ShopsSnapshot
import com.shopfinance.domain.money.Currency
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

/**
 * In-memory [ShopRepository] with one stream per uid. Tests send snapshots with [push] and
 * make the stream fail with [fail]. The latest event is replayed to new listeners, like a
 * snapshot listener that answers from the cache.
 */
class FakeShopRepository : ShopRepository {

    private val streams = mutableMapOf<String, MutableSharedFlow<Event>>()

    /** The uids passed to [observeShops], once per started listener. */
    val observeCalls = mutableListOf<String>()

    /** The (ownerUid, name) pairs passed to [createShop]. */
    val createCalls = mutableListOf<Pair<String, String>>()

    /** When set, [createShop] throws it instead of creating anything. */
    var createError: RuntimeException? = null

    fun push(uid: String, snapshot: ShopsSnapshot) {
        check(stream(uid).tryEmit(Event.Snapshot(snapshot)))
    }

    fun fail(uid: String, error: Throwable) {
        check(stream(uid).tryEmit(Event.Failure(error)))
    }

    /** How many listeners are currently collecting the shops of [uid]. */
    fun listenerCount(uid: String): Int = stream(uid).subscriptionCount.value

    override fun observeShops(uid: String): Flow<ShopsSnapshot> = flow {
        observeCalls += uid
        stream(uid).collect { event ->
            when (event) {
                is Event.Snapshot -> emit(event.snapshot)
                is Event.Failure -> throw event.error
            }
        }
    }

    /** The onRejected callback of the latest [createShop] call, to simulate a server refusal. */
    var lastOnRejected: ((Throwable) -> Unit)? = null

    override fun createShop(ownerUid: String, name: String, onRejected: (Throwable) -> Unit): String {
        createError?.let { throw it }
        createCalls += ownerUid to name
        lastOnRejected = onRejected
        return "shop-${createCalls.size}"
    }

    private fun stream(uid: String): MutableSharedFlow<Event> = streams.getOrPut(uid) {
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private sealed interface Event {
        data class Snapshot(val snapshot: ShopsSnapshot) : Event
        data class Failure(val error: Throwable) : Event
    }

    companion object {
        fun shop(id: String, name: String, ownerUid: String): Shop =
            Shop(id = id, name = name, ownerUid = ownerUid, currency = Currency.USD)
    }
}
