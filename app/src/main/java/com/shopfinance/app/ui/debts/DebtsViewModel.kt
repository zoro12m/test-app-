package com.shopfinance.app.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shopfinance.app.data.debts.DebtRejection
import com.shopfinance.app.data.debts.DebtRepository
import com.shopfinance.app.data.debts.DebtsSnapshot
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
import java.time.LocalDate

/** What the debtors list and a debtor's page show. */
sealed interface DebtsUiState {

    data object Loading : DebtsUiState

    data class Ready(val overview: DebtsOverview) : DebtsUiState

    /** Reading the debts failed; [accessDenied] when Firestore refused it. */
    data class Error(val accessDenied: Boolean) : DebtsUiState
}

/**
 * The debts of one shop, shared by the debtors list and the debtor pages while the shop is
 * open. Search and filter live here so they survive opening a person and coming back.
 * Deletes go through here; new debtors, debts and payments go through their form view models.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebtsViewModel(
    private val debts: DebtRepository,
    private val shopId: String,
    private val userUid: String,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    /** Bumped by [retry] to listen again after an error. */
    private val retryRequests = MutableStateFlow(0)

    val state: StateFlow<DebtsUiState> =
        retryRequests
            .flatMapLatest {
                debts.observeDebts(shopId)
                    .map<DebtsSnapshot, DebtsUiState> { snapshot -> DebtsUiState.Ready(DebtsOverview(snapshot, today())) }
                    .catch { error -> emit(DebtsUiState.Error(accessDenied = error is ShopAccessException)) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DebtsUiState.Loading,
            )

    private val _filter = MutableStateFlow(DebtorFilter.OPEN)
    val filter: StateFlow<DebtorFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _rejection = MutableStateFlow<DebtRejection?>(null)

    /** The latest write the server refused, until [dismissRejection]. */
    val rejection: StateFlow<DebtRejection?> = _rejection.asStateFlow()

    init {
        viewModelScope.launch {
            debts.rejections.collect { rejection -> _rejection.value = rejection }
        }
    }

    fun onFilterChange(filter: DebtorFilter) {
        _filter.value = filter
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun retry() {
        retryRequests.update { it + 1 }
    }

    fun dismissRejection() {
        _rejection.value = null
    }

    /** Marks the debt [debtId] deleted; it and its payments disappear from [state] at once. */
    fun deleteDebt(debtId: String) {
        debts.deleteDebt(shopId, debtId, userUid)
    }

    /** Marks the payment [paymentId] deleted; its debt's remaining goes back up at once. */
    fun deletePayment(paymentId: String) {
        debts.deletePayment(shopId, paymentId, userUid)
    }

    private companion object {
        /** Keeps the listeners alive through a configuration change such as a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
