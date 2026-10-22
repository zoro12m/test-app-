package com.shopfinance.app.ui.ledger

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.shopfinance.app.ui.debts.DebtFormScreen
import com.shopfinance.app.ui.debts.DebtFormViewModel
import com.shopfinance.app.ui.debts.DebtorFormScreen
import com.shopfinance.app.ui.debts.DebtorFormViewModel
import com.shopfinance.app.ui.debts.DebtorScreen
import com.shopfinance.app.ui.debts.DebtorsScreen
import com.shopfinance.app.ui.debts.DebtsUiState
import com.shopfinance.app.ui.debts.DebtsViewModel
import com.shopfinance.app.ui.debts.PaymentFormScreen
import com.shopfinance.app.ui.debts.PaymentFormViewModel
import java.util.UUID

/**
 * An open shop: the dashboard, the entries list and the add-entry form, the debts book with
 * its forms, a small back stack ([ShopRoute]) and the void dialog on top.
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
    // Shared by the debt screens; it only listens to Firestore while one of them is shown.
    val debtsViewModel: DebtsViewModel = viewModel(
        key = "debts-${shop.id}-${user.uid}",
        factory = viewModelFactory {
            initializer { DebtsViewModel(container.debtRepository, shop.id, user.uid) }
        },
    )

    var encodedStack by rememberSaveable { mutableStateOf(ShopRoute.INITIAL) }
    var voidingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val stack = ShopRoute.decode(encodedStack)

    val push: (ShopRoute) -> Unit = { route -> encodedStack = ShopRoute.encode(stack + route) }
    val pop: () -> Unit = { if (stack.size > 1) encodedStack = ShopRoute.encode(stack.dropLast(1)) }
    val replaceTop: (ShopRoute) -> Unit = { route -> encodedStack = ShopRoute.encode(stack.dropLast(1) + route) }
    val newFormId: () -> String = { UUID.randomUUID().toString() }

    BackHandler(enabled = stack.size > 1, onBack = pop)

    when (val route = stack.last()) {
        ShopRoute.Dashboard -> DashboardScreen(
            shop = shop,
            state = state,
            rejection = rejection,
            onDismissRejection = shopViewModel::dismissRejection,
            onNewEntry = { type -> push(ShopRoute.NewEntry(type, newFormId())) },
            onShowAll = { push(ShopRoute.Entries) },
            onOpenDebts = { push(ShopRoute.Debtors) },
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

        ShopRoute.Debtors -> {
            val debtsState by debtsViewModel.state.collectAsStateWithLifecycle()
            val filter by debtsViewModel.filter.collectAsStateWithLifecycle()
            val query by debtsViewModel.query.collectAsStateWithLifecycle()
            val debtRejection by debtsViewModel.rejection.collectAsStateWithLifecycle()
            DebtorsScreen(
                state = debtsState,
                filter = filter,
                query = query,
                rejection = debtRejection,
                onFilterChange = debtsViewModel::onFilterChange,
                onQueryChange = debtsViewModel::onQueryChange,
                onDismissRejection = debtsViewModel::dismissRejection,
                onOpenDebtor = { debtorId -> push(ShopRoute.Debtor(debtorId)) },
                onAddDebtor = { push(ShopRoute.NewDebtor(newFormId())) },
                onRetry = debtsViewModel::retry,
                onBack = pop,
            )
        }

        is ShopRoute.Debtor -> {
            val debtsState by debtsViewModel.state.collectAsStateWithLifecycle()
            val debtRejection by debtsViewModel.rejection.collectAsStateWithLifecycle()
            DebtorScreen(
                state = debtsState,
                debtorId = route.debtorId,
                rejection = debtRejection,
                onDismissRejection = debtsViewModel::dismissRejection,
                onAddDebt = { push(ShopRoute.NewDebt(route.debtorId, newFormId())) },
                onRecordPayment = { debtId -> push(ShopRoute.NewPayment(debtId, newFormId())) },
                onDeleteDebt = debtsViewModel::deleteDebt,
                onDeletePayment = debtsViewModel::deletePayment,
                onRetry = debtsViewModel::retry,
                onBack = pop,
            )
        }

        is ShopRoute.NewDebtor -> {
            val formViewModel: DebtorFormViewModel = viewModel(
                key = "debtor-form-${route.formId}",
                factory = viewModelFactory {
                    initializer { DebtorFormViewModel(container.debtRepository, shop.id, user.uid) }
                },
            )
            // Once saved, the form gives way to the new person's page, ready for their first debt.
            DebtorFormScreen(
                viewModel = formViewModel,
                onSaved = { debtorId -> replaceTop(ShopRoute.Debtor(debtorId)) },
                onBack = pop,
            )
        }

        is ShopRoute.NewDebt -> {
            val debtsState by debtsViewModel.state.collectAsStateWithLifecycle()
            val formViewModel: DebtFormViewModel = viewModel(
                key = "debt-form-${route.formId}",
                factory = viewModelFactory {
                    initializer { DebtFormViewModel(container.debtRepository, shop.id, user.uid, route.debtorId) }
                },
            )
            DebtFormScreen(
                viewModel = formViewModel,
                debtorName = (debtsState as? DebtsUiState.Ready)?.overview?.row(route.debtorId)?.debtor?.name,
                onDone = pop,
            )
        }

        is ShopRoute.NewPayment -> {
            val formViewModel: PaymentFormViewModel = viewModel(
                key = "payment-form-${route.formId}",
                factory = viewModelFactory {
                    initializer { PaymentFormViewModel(container.debtRepository, shop.id, user.uid, route.debtId) }
                },
            )
            // The form reads the debt from the shared debts state rather than listening itself.
            val debtsState by debtsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(formViewModel, debtsState) { formViewModel.onDebtsChange(debtsState) }
            PaymentFormScreen(viewModel = formViewModel, onRetry = debtsViewModel::retry, onDone = pop)
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
