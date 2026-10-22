package com.shopfinance.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shopfinance.app.data.auth.AuthRepository
import com.shopfinance.app.data.auth.AuthUser
import com.shopfinance.app.data.shop.ShopAccessException
import com.shopfinance.app.data.shop.ShopRepository
import com.shopfinance.app.data.shop.ShopsSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Turns the signed-in user and their shops into one [SessionState].
 *
 * A new user (sign-in, sign-out, another account) or a [retry] cancels the previous shop
 * listener and starts a fresh one. Screens never navigate themselves: signing in, creating
 * a shop or signing out changes what the repositories emit, and [state] follows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val auth: AuthRepository,
    private val shops: ShopRepository,
) : ViewModel() {

    /** Bumped by [retry]. A new value re-emits the current user, which restarts the shop listener. */
    private val retryRequests = MutableStateFlow(0)

    val state: StateFlow<SessionState> =
        combine(auth.currentUser, retryRequests) { user, _ -> user }
            .flatMapLatest { user ->
                if (user == null) flowOf(SessionState.SignedOut) else shopStates(user)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SessionState.Loading,
            )

    /** Listens to the shops again after a [SessionState.ShopsError]. */
    fun retry() {
        retryRequests.update { it + 1 }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun shopStates(user: AuthUser): Flow<SessionState> =
        shops.observeShops(user.uid)
            .map { snapshot -> snapshot.toSessionState(user) }
            .onStart { emit(SessionState.LoadingShops(user)) }
            .catch { error ->
                emit(SessionState.ShopsError(user, accessDenied = error is ShopAccessException))
            }

    private fun ShopsSnapshot.toSessionState(user: AuthUser): SessionState {
        // Stage 2 has one shop per person; the repository sorts by name, so this is stable.
        val shop = shops.firstOrNull()
        return when {
            shop != null -> SessionState.Ready(user, shop)
            fromCache -> SessionState.LoadingShops(user)
            else -> SessionState.NeedsShop(user)
        }
    }

    private companion object {
        /** Keeps the listeners alive through a configuration change such as a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
