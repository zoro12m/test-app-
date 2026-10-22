package com.shopfinance.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopfinance.app.AppContainer
import com.shopfinance.app.R
import com.shopfinance.app.ui.auth.AuthScreen
import com.shopfinance.app.ui.auth.AuthViewModel
import com.shopfinance.app.ui.ledger.ShopHost
import com.shopfinance.app.ui.session.SessionState
import com.shopfinance.app.ui.session.SessionViewModel
import com.shopfinance.app.ui.shop.ShopSetupScreen
import com.shopfinance.app.ui.shop.ShopSetupViewModel

/**
 * The whole app: picks the screen from the [SessionState]. There is no navigation
 * library; signing in, creating a shop and signing out change the state, and this follows.
 */
@Composable
fun AppRoot(container: AppContainer) {
    if (container.firebaseConfigured) {
        SessionRoot(container)
    } else {
        FirebaseMissingScreen()
    }
}

@Composable
private fun SessionRoot(container: AppContainer) {
    val sessionViewModel: SessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SessionViewModel(container.authRepository, container.shopRepository) }
        },
    )
    val state by sessionViewModel.state.collectAsStateWithLifecycle()

    when (val session = state) {
        SessionState.Loading -> LoadingScreen(message = stringResource(R.string.loading_session))

        is SessionState.LoadingShops -> LoadingScreen(
            message = stringResource(R.string.loading_shop),
            hint = stringResource(R.string.loading_shop_hint),
        )

        SessionState.SignedOut -> {
            val authViewModel: AuthViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { AuthViewModel(container.authRepository) }
                },
            )
            AuthScreen(viewModel = authViewModel)
        }

        is SessionState.NeedsShop -> {
            val uid = session.user.uid
            // Keyed on the user: another account signing in on this device gets a fresh form.
            val setupViewModel: ShopSetupViewModel = viewModel(
                key = "shop-setup-$uid",
                factory = viewModelFactory {
                    initializer { ShopSetupViewModel(container.shopRepository, uid) }
                },
            )
            ShopSetupScreen(viewModel = setupViewModel, onSignOut = sessionViewModel::signOut)
        }

        is SessionState.Ready -> ShopHost(
            container = container,
            user = session.user,
            shop = session.shop,
            onSignOut = sessionViewModel::signOut,
        )

        is SessionState.ShopsError -> ShopsErrorScreen(
            accessDenied = session.accessDenied,
            onRetry = sessionViewModel::retry,
            onSignOut = sessionViewModel::signOut,
        )
    }
}
