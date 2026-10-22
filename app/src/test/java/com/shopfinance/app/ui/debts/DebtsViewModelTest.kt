package com.shopfinance.app.ui.debts

import com.shopfinance.app.data.debts.DebtRejection
import com.shopfinance.app.data.debts.DebtsSnapshot
import com.shopfinance.app.data.shop.ShopAccessException
import com.shopfinance.app.ui.fakes.FakeDebtRepository
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.debt
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.debtor
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.payment
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.snapshot
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import com.shopfinance.domain.money.MoneyTotals
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DebtsViewModelTest {

    private val today = LocalDate.of(2026, 10, 22)
    private lateinit var debts: FakeDebtRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        debts = FakeDebtRepository(today)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun usd(dollars: Long) = Money(dollars * 100, Currency.USD)

    /** A view model whose state is being collected, as it is while a screen shows it. */
    private fun TestScope.collectedViewModel(): DebtsViewModel {
        val viewModel = DebtsViewModel(debts, shopId = "s1", userUid = "alice", today = { today })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private val DebtsViewModel.overview: DebtsOverview get() = (state.value as DebtsUiState.Ready).overview

    @Test
    fun nothingIsObservedBeforeAnyoneCollects() = runTest {
        val viewModel = DebtsViewModel(debts, shopId = "s1", userUid = "alice", today = { today })
        runCurrent()

        assertEquals(DebtsUiState.Loading, viewModel.state.value)
        assertTrue(debts.observeCalls.isEmpty())
    }

    @Test
    fun staysLoadingUntilTheFirstSnapshot() = runTest {
        val viewModel = collectedViewModel()

        assertEquals(listOf("s1"), debts.observeCalls)
        assertEquals(DebtsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun aSnapshotBecomesTheOverview() = runTest {
        val viewModel = collectedViewModel()
        debts.push("s1", snapshot(listOf(debtor("r", "رامي")), listOf(debt("d1", "r", usd(100), "2026-10-01"))))
        runCurrent()

        val overview = viewModel.overview
        assertEquals(today, overview.today)
        assertEquals(MoneyTotals.of(usd(100)), overview.totalRemaining)
        assertEquals(21L, overview.row("r")!!.daysSinceOldestUnpaid)
    }

    @Test
    fun anEmptyShopIsReadyWithNothingOwed() = runTest {
        val viewModel = collectedViewModel()
        debts.push("s1", DebtsSnapshot.EMPTY)
        runCurrent()

        assertTrue(viewModel.overview.rows.isEmpty())
        assertTrue(viewModel.overview.totalRemaining.isZero)
    }

    @Test
    fun refusedAccessIsReportedAsSuch() = runTest {
        val viewModel = collectedViewModel()
        debts.fail("s1", ShopAccessException())
        runCurrent()

        assertEquals(DebtsUiState.Error(accessDenied = true), viewModel.state.value)
    }

    @Test
    fun otherFailuresAreReportedAsGeneric() = runTest {
        val viewModel = collectedViewModel()
        debts.fail("s1", IOException("offline"))
        runCurrent()

        assertEquals(DebtsUiState.Error(accessDenied = false), viewModel.state.value)
    }

    @Test
    fun retryListensAgain() = runTest {
        val viewModel = collectedViewModel()
        debts.fail("s1", IOException("offline"))
        runCurrent()

        debts.push("s1", DebtsSnapshot.EMPTY)
        viewModel.retry()
        runCurrent()

        assertEquals(listOf("s1", "s1"), debts.observeCalls)
        assertTrue(viewModel.state.value is DebtsUiState.Ready)
    }

    @Test
    fun theListStartsOnOpenWithNoSearch() = runTest {
        val viewModel = collectedViewModel()
        assertEquals(DebtorFilter.OPEN, viewModel.filter.value)
        assertEquals("", viewModel.query.value)

        viewModel.onFilterChange(DebtorFilter.PAID)
        viewModel.onQueryChange("رامي")
        assertEquals(DebtorFilter.PAID, viewModel.filter.value)
        assertEquals("رامي", viewModel.query.value)
    }

    @Test
    fun deletingADebtHidesItAndItsPaymentsAtOnce() = runTest {
        val viewModel = collectedViewModel()
        debts.push(
            "s1",
            snapshot(
                listOf(debtor("r", "رامي")),
                listOf(debt("d1", "r", usd(100), "2026-10-01"), debt("d2", "r", usd(20), "2026-10-05")),
                listOf(payment("p1", "d1", usd(30), "2026-10-10")),
            ),
        )
        runCurrent()

        viewModel.deleteDebt("d1")
        runCurrent()

        assertEquals(listOf(FakeDebtRepository.DeleteCall("s1", "d1", "alice")), debts.deleteDebtCalls)
        assertEquals(listOf("d2"), viewModel.overview.cards(viewModel.overview.row("r")!!).map { it.account.debt.id })
        assertEquals(MoneyTotals.of(usd(20)), viewModel.overview.totalRemaining)
    }

    @Test
    fun deletingAPaymentPutsTheRemainingBackUp() = runTest {
        val viewModel = collectedViewModel()
        debts.push(
            "s1",
            snapshot(
                listOf(debtor("r", "رامي")),
                listOf(debt("d1", "r", usd(100), "2026-10-01")),
                listOf(payment("p1", "d1", usd(30), "2026-10-10")),
            ),
        )
        runCurrent()

        viewModel.deletePayment("p1")
        runCurrent()

        assertEquals(listOf(FakeDebtRepository.DeleteCall("s1", "p1", "alice")), debts.deletePaymentCalls)
        assertEquals(usd(100), viewModel.overview.account("d1")!!.remaining)
    }

    @Test
    fun serverRefusalsAreShownUntilDismissed() = runTest {
        val viewModel = collectedViewModel()
        val rejection = DebtRejection(DebtRejection.Kind.ADD_PAYMENT, "p1", accessDenied = true)
        debts.reject(rejection)
        runCurrent()

        assertEquals(rejection, viewModel.rejection.value)

        viewModel.dismissRejection()
        assertNull(viewModel.rejection.value)
    }
}
