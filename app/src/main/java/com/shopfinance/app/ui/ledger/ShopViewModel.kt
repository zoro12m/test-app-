package com.shopfinance.app.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shopfinance.app.data.ledger.LedgerInputRules
import com.shopfinance.app.data.ledger.LedgerRecord
import com.shopfinance.app.data.ledger.LedgerRejection
import com.shopfinance.app.data.ledger.LedgerRepository
import com.shopfinance.app.data.ledger.LedgerSnapshot
import com.shopfinance.app.data.ledger.VoidReasonError
import com.shopfinance.app.data.shop.ShopAccessException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** What the dashboard and the entries list show. */
sealed interface LedgerUiState {

    data object Loading : LedgerUiState

    /**
     * @property records every entry, newest first, voided ones included.
     * @property pendingCount entries (or voids) the device has not sent to the server yet.
     */
    data class Ready(
        val summary: DashboardSummary,
        val records: List<LedgerRecord>,
        val pendingCount: Int,
    ) : LedgerUiState

    /** Reading the ledger failed; [accessDenied] when Firestore refused it. */
    data class Error(val accessDenied: Boolean) : LedgerUiState
}

/**
 * The ledger of one shop, shared by the dashboard and the entries list while the shop is
 * open. Voids go through here; new entries go through [EntryFormViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModel(
    private val ledger: LedgerRepository,
    private val shopId: String,
    private val userUid: String,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    /** Bumped by [retry] to listen again after an error. */
    private val retryRequests = MutableStateFlow(0)

    val state: StateFlow<LedgerUiState> =
        retryRequests
            .flatMapLatest {
                ledger.observeEntries(shopId)
                    .map<LedgerSnapshot, LedgerUiState> { snapshot -> snapshot.toUiState() }
                    .catch { error -> emit(LedgerUiState.Error(accessDenied = error is ShopAccessException)) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = LedgerUiState.Loading,
            )

    private val _rejection = MutableStateFlow<LedgerRejection?>(null)

    /** The latest write the server refused, until [dismissRejection]. */
    val rejection: StateFlow<LedgerRejection?> = _rejection.asStateFlow()

    init {
        viewModelScope.launch {
            ledger.rejections.collect { rejection -> _rejection.value = rejection }
        }
    }

    fun retry() {
        retryRequests.update { it + 1 }
    }

    fun dismissRejection() {
        _rejection.value = null
    }

    /**
     * Voids the entry [entryId]. Returns why [reason] is not accepted, or null when the void
     * was queued (it shows in [state] at once).
     */
    fun voidEntry(entryId: String, reason: String): VoidReasonError? {
        LedgerInputRules.validateVoidReason(reason)?.let { return it }
        ledger.voidEntry(shopId, entryId, userUid, reason)
        return null
    }

    private fun LedgerSnapshot.toUiState(): LedgerUiState = LedgerUiState.Ready(
        summary = DashboardSummary.of(records.map { it.entry }, clock(), zone()),
        records = records,
        pendingCount = records.count { it.pendingSync },
    )

    private companion object {
        /** Keeps the listener alive through a configuration change such as a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
