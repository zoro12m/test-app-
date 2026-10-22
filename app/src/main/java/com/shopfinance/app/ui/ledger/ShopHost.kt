package com.shopfinance.app.ui.ledger

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopfinance.app.AppContainer
import com.shopfinance.app.data.auth.AuthUser
import com.shopfinance.app.data.shop.Shop
import java.util.UUID

/**
 * An open shop: the dashboard, the entries list and the add-entry form, with a small back
 * stack ([ShopRoute]) and the void dialog on top.
 */
@Composable
internal fun ShopHost(
    container: AppContainer,
    user: AuthUser,
    shop: Shop,
    onSignOut: () -> Unit,
) {
    // Keyed on shop and user: another account on this device gets its own ledger.
    val shopViewModel: ShopViewModel = viewModel(
        key = "ledger-${shop.id}-${user.uid}",
        factory = viewModelFactory {
            initializer { ShopViewModel(container.ledgerRepository, shop.id, user.uid) }
        },
    )
    val state by shopViewModel.state.collectAsStateWithLifecycle()
    val rejection by shopViewModel.rejection.collectAsStateWithLifecycle()

    var encodedStack by rememberSaveable { mutableStateOf(ShopRoute.INITIAL) }
    var voidingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val stack = ShopRoute.decode(encodedStack)

    val push: (ShopRoute) -> Unit = { route -> encodedStack = ShopRoute.encode(stack + route) }
    val pop: () -> Unit = { if (stack.size > 1) encodedStack = ShopRoute.encode(stack.dropLast(1)) }

    BackHandler(enabled = stack.size > 1, onBack = pop)

    when (val route = stack.last()) {
        ShopRoute.Dashboard -> DashboardScreen(
            shop = shop,
            state = state,
            rejection = rejection,
            onDismissRejection = shopViewModel::dismissRejection,
            onNewEntry = { type -> push(ShopRoute.NewEntry(type, UUID.randomUUID().toString())) },
            onShowAll = { push(ShopRoute.Entries) },
            onVoid = { record -> voidingEntryId = record.entry.id },
            onRetry = shopViewModel::retry,
            onSignOut = onSignOut,
        )

        ShopRoute.Entries -> EntriesScreen(
            state = state,
            onBack = pop,
            onVoid = { record -> voidingEntryId = record.entry.id },
            onRetry = shopViewModel::retry,
        )

        is ShopRoute.NewEntry -> {
            val formViewModel: EntryFormViewModel = viewModel(
                key = "entry-form-${route.formId}",
                factory = viewModelFactory {
                    initializer { EntryFormViewModel(container.ledgerRepository, shop.id, user.uid, route.type) }
                },
            )
            EntryFormScreen(viewModel = formViewModel, onDone = pop)
        }
    }

    val voiding = (state as? LedgerUiState.Ready)?.records
        ?.firstOrNull { it.entry.id == voidingEntryId && !it.entry.isVoided }
    if (voiding != null) {
        VoidEntryDialog(
            record = voiding,
            onConfirm = { reason -> shopViewModel.voidEntry(voiding.entry.id, reason) },
            onDismiss = { voidingEntryId = null },
        )
    }
}
