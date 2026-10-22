package com.shopfinance.app.ui.shop

import com.shopfinance.app.data.shop.ShopNameError
import com.shopfinance.app.data.shop.ShopNameRules
import com.shopfinance.app.ui.fakes.FakeShopRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ShopSetupViewModel] runs no coroutines (createShop returns at once), so no test dispatcher is needed. */
class ShopSetupViewModelTest {

    private val shops = FakeShopRepository()
    private val viewModel = ShopSetupViewModel(shops, ownerUid = "alice")

    private val state: ShopSetupUiState get() = viewModel.state.value

    @Test
    fun startsWithAnEmptyForm() {
        assertEquals(ShopSetupUiState(), state)
    }

    @Test
    fun blankNameIsRejectedAndNothingIsCreated() {
        viewModel.onNameChange("   ")
        viewModel.submit()

        assertEquals(ShopNameError.EMPTY, state.nameError)
        assertFalse(state.isCreating)
        assertTrue(shops.createCalls.isEmpty())
    }

    @Test
    fun nameOverTheMaximumIsRejected() {
        viewModel.onNameChange("م".repeat(ShopNameRules.MAX_LENGTH + 1))
        viewModel.submit()

        assertEquals(ShopNameError.TOO_LONG, state.nameError)
        assertTrue(shops.createCalls.isEmpty())
    }

    @Test
    fun nameOfExactlyTheMaximumIsAccepted() {
        val name = "م".repeat(ShopNameRules.MAX_LENGTH)
        viewModel.onNameChange(name)
        viewModel.submit()

        assertNull(state.nameError)
        assertEquals(listOf("alice" to name), shops.createCalls)
    }

    @Test
    fun validNameCreatesTheShopForTheOwnerWithTheNormalizedName() {
        viewModel.onNameChange("  سوبرماركت   الأمل ")
        viewModel.submit()

        assertEquals(listOf("alice" to "سوبرماركت الأمل"), shops.createCalls)
        assertTrue(state.isCreating)
        assertNull(state.nameError)
        assertFalse(state.createFailed)
    }

    @Test
    fun repeatedSubmitsCreateTheShopOnlyOnce() {
        viewModel.onNameChange("سوبرماركت الأمل")
        viewModel.submit()
        viewModel.submit()
        viewModel.submit()

        assertEquals(1, shops.createCalls.size)
    }

    @Test
    fun nameCannotBeEditedOnceTheShopIsCreated() {
        viewModel.onNameChange("سوبرماركت الأمل")
        viewModel.submit()

        viewModel.onNameChange("اسم آخر")

        assertEquals("سوبرماركت الأمل", state.name)
    }

    @Test
    fun editingTheNameClearsItsError() {
        viewModel.submit()
        assertEquals(ShopNameError.EMPTY, state.nameError)

        viewModel.onNameChange("س")

        assertNull(state.nameError)
        assertEquals("س", state.name)
    }

    @Test
    fun failedCreateIsReportedAndCanBeRetried() {
        shops.createError = IllegalStateException("Firestore is not available")
        viewModel.onNameChange("سوبرماركت الأمل")
        viewModel.submit()

        assertTrue(state.createFailed)
        assertFalse(state.isCreating)
        assertTrue(shops.createCalls.isEmpty())

        shops.createError = null
        viewModel.submit()

        assertFalse(state.createFailed)
        assertTrue(state.isCreating)
        assertEquals(listOf("alice" to "سوبرماركت الأمل"), shops.createCalls)
    }

    @Test
    fun serverRejectionUnlocksTheFormForAnotherTry() {
        viewModel.onNameChange("سوبرماركت الأمل")
        viewModel.submit()
        assertTrue(state.isCreating)

        shops.lastOnRejected!!.invoke(IllegalStateException("PERMISSION_DENIED"))

        assertFalse(state.isCreating)
        assertTrue(state.createFailed)

        viewModel.onNameChange("محل الأمل")
        viewModel.submit()

        assertTrue(state.isCreating)
        assertEquals(listOf("alice" to "سوبرماركت الأمل", "alice" to "محل الأمل"), shops.createCalls)
    }
}
