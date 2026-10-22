package com.shopfinance.app.ui.session

import com.shopfinance.app.data.auth.AuthUser
import com.shopfinance.app.data.shop.ShopAccessException
import com.shopfinance.app.data.shop.ShopsSnapshot
import com.shopfinance.app.ui.fakes.FakeAuthRepository
import com.shopfinance.app.ui.fakes.FakeShopRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val alice = AuthUser(uid = "alice", email = "alice@example.com", displayName = "Alice")
    private val bob = AuthUser(uid = "bob", email = "bob@example.com", displayName = null)
    private val aliceShop = FakeShopRepository.shop(id = "s1", name = "سوبرماركت الأمل", ownerUid = "alice")
    private val aliceSecondShop = FakeShopRepository.shop(id = "s2", name = "مكتبة النور", ownerUid = "alice")

    private lateinit var auth: FakeAuthRepository
    private lateinit var shops: FakeShopRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        auth = FakeAuthRepository()
        shops = FakeShopRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is being collected, as it is while a screen shows it. */
    private fun TestScope.collectedViewModel(): SessionViewModel {
        val viewModel = SessionViewModel(auth, shops)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun stateIsLoadingAndNothingIsObservedBeforeAnyoneCollects() = runTest {
        auth.user.value = alice
        val viewModel = SessionViewModel(auth, shops)
        runCurrent()

        assertEquals(SessionState.Loading, viewModel.state.value)
        assertTrue(shops.observeCalls.isEmpty())
    }

    @Test
    fun noUserMeansSignedOut() = runTest {
        val viewModel = collectedViewModel()

        assertEquals(SessionState.SignedOut, viewModel.state.value)
        assertTrue(shops.observeCalls.isEmpty())
    }

    @Test
    fun signedInUserWaitsForTheShopList() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()

        assertEquals(SessionState.LoadingShops(alice), viewModel.state.value)
        assertEquals(listOf("alice"), shops.observeCalls)
    }

    @Test
    fun firstShopOfTheListMakesTheSessionReady() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()

        shops.push("alice", ShopsSnapshot(listOf(aliceShop, aliceSecondShop), fromCache = false))
        runCurrent()

        assertEquals(SessionState.Ready(alice, aliceShop), viewModel.state.value)
    }

    @Test
    fun cachedShopIsEnoughToBeReady() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()

        shops.push("alice", ShopsSnapshot(listOf(aliceShop), fromCache = true))
        runCurrent()

        assertEquals(SessionState.Ready(alice, aliceShop), viewModel.state.value)
    }

    @Test
    fun emptyListFromTheCacheKeepsLoadingUntilTheServerConfirms() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()

        shops.push("alice", ShopsSnapshot(emptyList(), fromCache = true))
        runCurrent()
        assertEquals(SessionState.LoadingShops(alice), viewModel.state.value)

        shops.push("alice", ShopsSnapshot(emptyList(), fromCache = false))
        runCurrent()
        assertEquals(SessionState.NeedsShop(alice), viewModel.state.value)
    }

    @Test
    fun shopCreatedDuringSetupMakesTheSessionReady() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()
        shops.push("alice", ShopsSnapshot(emptyList(), fromCache = false))
        runCurrent()
        assertEquals(SessionState.NeedsShop(alice), viewModel.state.value)

        // A local write shows up from the cache before the server has it.
        shops.push("alice", ShopsSnapshot(listOf(aliceShop), fromCache = true))
        runCurrent()

        assertEquals(SessionState.Ready(alice, aliceShop), viewModel.state.value)
        assertEquals(listOf("alice"), shops.observeCalls)
    }

    @Test
    fun refusedReadIsReportedAsAccessDenied() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()

        shops.fail("alice", ShopAccessException())
        runCurrent()

        assertEquals(SessionState.ShopsError(alice, accessDenied = true), viewModel.state.value)
    }

    @Test
    fun otherFailuresAreNotReportedAsAccessDenied() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()

        shops.fail("alice", IOException("offline"))
        runCurrent()

        assertEquals(SessionState.ShopsError(alice, accessDenied = false), viewModel.state.value)
    }

    @Test
    fun retryListensToTheShopsAgain() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()
        shops.fail("alice", ShopAccessException())
        runCurrent()
        assertEquals(SessionState.ShopsError(alice, accessDenied = true), viewModel.state.value)

        shops.push("alice", ShopsSnapshot(listOf(aliceShop), fromCache = false))
        viewModel.retry()
        runCurrent()

        assertEquals(SessionState.Ready(alice, aliceShop), viewModel.state.value)
        assertEquals(listOf("alice", "alice"), shops.observeCalls)
        assertEquals(1, shops.listenerCount("alice"))
    }

    @Test
    fun signOutSignsOutAndStopsTheShopListener() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()
        shops.push("alice", ShopsSnapshot(listOf(aliceShop), fromCache = false))
        runCurrent()
        assertEquals(1, shops.listenerCount("alice"))

        viewModel.signOut()
        runCurrent()

        assertEquals(1, auth.signOutCalls)
        assertEquals(SessionState.SignedOut, viewModel.state.value)
        assertEquals(0, shops.listenerCount("alice"))
    }

    @Test
    fun anotherAccountGetsItsOwnShopList() = runTest {
        auth.user.value = alice
        val viewModel = collectedViewModel()
        shops.push("alice", ShopsSnapshot(listOf(aliceShop), fromCache = false))
        runCurrent()

        auth.user.value = bob
        runCurrent()
        assertEquals(SessionState.LoadingShops(bob), viewModel.state.value)
        assertEquals(listOf("alice", "bob"), shops.observeCalls)
        assertEquals(0, shops.listenerCount("alice"))

        shops.push("bob", ShopsSnapshot(emptyList(), fromCache = false))
        runCurrent()
        assertEquals(SessionState.NeedsShop(bob), viewModel.state.value)
    }

    @Test
    fun signingInAfterSignOutStartsAFreshShopListener() = runTest {
        val viewModel = collectedViewModel()
        assertEquals(SessionState.SignedOut, viewModel.state.value)

        auth.user.value = alice
        runCurrent()
        assertEquals(SessionState.LoadingShops(alice), viewModel.state.value)

        shops.push("alice", ShopsSnapshot(listOf(aliceShop), fromCache = false))
        runCurrent()
        assertEquals(SessionState.Ready(alice, aliceShop), viewModel.state.value)
    }
}
