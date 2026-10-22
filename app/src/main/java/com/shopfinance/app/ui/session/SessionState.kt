package com.shopfinance.app.ui.session

import com.shopfinance.app.data.auth.AuthUser
import com.shopfinance.app.data.shop.Shop

/** Where the person is in the app's flow; [com.shopfinance.app.ui.AppRoot] shows one screen per state. */
sealed interface SessionState {

    /** Not known yet whether anyone is signed in. */
    data object Loading : SessionState

    data object SignedOut : SessionState

    /**
     * Signed in, and the shop list is not known yet: the server has not answered, or only
     * an empty list from the device cache has arrived, which does not prove there is no shop.
     */
    data class LoadingShops(val user: AuthUser) : SessionState

    /** Signed in, and the server confirmed that the person belongs to no shop. */
    data class NeedsShop(val user: AuthUser) : SessionState

    data class Ready(val user: AuthUser, val shop: Shop) : SessionState

    /**
     * Reading the shop list failed. [accessDenied] is true when Firestore refused the read,
     * which usually means the security rules are not published.
     */
    data class ShopsError(val user: AuthUser, val accessDenied: Boolean) : SessionState
}
