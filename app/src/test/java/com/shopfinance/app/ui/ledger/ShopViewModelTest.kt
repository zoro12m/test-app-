package com.shopfinance.app.ui.ledger

import com.shopfinance.app.data.ledger.LedgerInputRules
import com.shopfinance.app.data.ledger.LedgerRejection
import com.shopfinance.app.data.ledger.LedgerSnapshot
import com.shopfinance.app.data.ledger.VoidReasonError
import com.shopfinance.app.data.shop.ShopAccessException
import com.shopfinance.app.ui.fakes.FakeLedgerRepository
import com.shopfinance.app.ui.fakes.FakeLedgerRepository.Companion.record
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModelTest {

    private val now = Instant.parse("2026-10-22T11:00:00Z")
    private val beirut = ZoneId.of("Asia/Beirut")
    private lateinit var ledger: FakeLedgerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ledger = FakeLedgerRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ShopViewModel(ledger, shopId = "s1", userUid = "alice", clock = { now }, zone = { beirut })

    /** A view model whose state is being collected, as it is while a screen shows it. */
    private fun TestScope.collectedViewModel(): ShopViewModel {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun nothingIsObservedBeforeAnyoneCollects() = runTest {
        val viewModel = newViewModel()
        runCurrent()

        assertEquals(LedgerUiState.Loading, viewModel.state.value)
        assertTrue(ledger.observeCalls.isEmpty())
    }

    @Test
    fun staysLoadingUntilTheFirstSnapshot() = runTest {
        val viewModel = collectedViewModel()

        assertEquals(listOf("s1"), ledger.observeCalls)
        assertEquals(LedgerUiState.Loading, viewModel.state.value)
    }

    @Test
    fun aSnapshotBecomesTheSummaryAndTheList() = runTest {
        val viewModel = collectedViewModel()
        val records = listOf(
            record("2", EntryType.EXPENSE, 1_500, Instant.parse("2026-10-22T10:00:00Z"), "rent", pendingSync = true),
            record("1", EntryType.SALE, 4_000, Instant.parse("2026-10-22T09:00:00Z")),
        )
        ledger.push("s1", LedgerSnapshot(records, fromCache = false))
        runCurrent()

        val state = viewModel.state.value as LedgerUiState.Ready
        assertEquals(records, state.records)
        assertEquals(1, state.pendingCount)
        assertEquals(Money(2_500, Currency.USD), state.summary.cashOnHand)
        assertEquals(Money(4_000, Currency.USD), state.summary.todaySales)
        assertEquals(Money(1_500, Currency.USD), state.summary.todayExpenses)
    }

    @Test
    fun anEmptyLedgerIsReadyWithZeroes() = runTest {
        val viewModel = collectedViewModel()
        ledger.push("s1", LedgerSnapshot(emptyList(), fromCache = true))
        runCurrent()

        assertEquals(LedgerUiState.Ready(DashboardSummary.EMPTY, emptyList(), 0), viewModel.state.value)
    }

    @Test
    fun refusedAccessIsReportedAsSuch() = runTest {
        val viewModel = collectedViewModel()
        ledger.fail("s1", ShopAccessException())
        runCurrent()

        assertEquals(LedgerUiState.Error(accessDenied = true), viewModel.state.value)
    }

    @Test
    fun otherFailuresAreReportedAsGeneric() = runTest {
        val viewModel = collectedViewModel()
        ledger.fail("s1", IOException("offline"))
        runCurrent()

        assertEquals(LedgerUiState.Error(accessDenied = false), viewModel.state.value)
    }

    @Test
    fun retryListensAgain() = runTest {
        val viewModel = collectedViewModel()
        ledger.fail("s1", ShopAccessException())
        runCurrent()

        ledger.push("s1", LedgerSnapshot(emptyList(), fromCache = false))
        viewModel.retry()
        runCurrent()

        assertEquals(listOf("s1", "s1"), ledger.observeCalls)
        assertTrue(viewModel.state.value is LedgerUiState.Ready)
    }

    @Test
    fun serverRefusalsAreShownUntilDismissed() = runTest {
        val viewModel = collectedViewModel()
        val rejection = LedgerRejection(LedgerRejection.Kind.ADD, "e1", accessDenied = false)
        ledger.reject(rejection)
        runCurrent()

        assertEquals(rejection, viewModel.rejection.value)

        viewModel.dismissRejection()
        assertNull(viewModel.rejection.value)
    }

    @Test
    fun aVoidNeedsAReason() = runTest {
        val viewModel = collectedViewModel()

        assertEquals(VoidReasonError.EMPTY, viewModel.voidEntry("e1", "   "))
        assertEquals(
            VoidReasonError.TOO_LONG,
            viewModel.voidEntry("e1", "س".repeat(LedgerInputRules.VOID_REASON_MAX_LENGTH + 1)),
        )
        assertTrue(ledger.voidCalls.isEmpty())
    }

    @Test
    fun aVoidIsSentForTheShopAndPerson() = runTest {
        val viewModel = collectedViewModel()

        assertNull(viewModel.voidEntry("e1", "سُجّل مرتين"))
        assertEquals(listOf(FakeLedgerRepository.VoidCall("s1", "e1", "alice", "سُجّل مرتين")), ledger.voidCalls)
    }
}
