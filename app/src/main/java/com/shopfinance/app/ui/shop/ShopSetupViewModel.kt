package com.shopfinance.app.ui.shop

import androidx.lifecycle.ViewModel
import com.shopfinance.app.data.shop.ShopNameError
import com.shopfinance.app.data.shop.ShopNameRules
import com.shopfinance.app.data.shop.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the shop setup screen shows. */
data class ShopSetupUiState(
    val name: String = "",
    val nameError: ShopNameError? = null,
    /**
     * True once the shop was handed to the repository. It stays true unless the shop is
     * refused: the new shop reaches the session from the local cache right away and the app
     * moves on to the home screen.
     */
    val isCreating: Boolean = false,
    /** The repository or the server refused the shop; the person can try again. */
    val createFailed: Boolean = false,
)

/**
 * Creates the first shop of the user [ownerUid]. Every new shop uses USD: there is no
 * currency choice. [ShopRepository.createShop] is called at most once per attempt: while a
 * shop is being created, [submit] and [onNameChange] do nothing. If the server refuses the
 * shop later, the session comes back to setup and this form unlocks with an error.
 */
class ShopSetupViewModel(
    private val shops: ShopRepository,
    private val ownerUid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ShopSetupUiState())
    val state: StateFlow<ShopSetupUiState> = _state.asStateFlow()

    fun onNameChange(value: String) {
        _state.update {
            if (it.isCreating) it else it.copy(name = value, nameError = null, createFailed = false)
        }
    }

    fun submit() {
        val form = _state.value
        if (form.isCreating) return
        val nameError = ShopNameRules.validate(form.name)
        if (nameError != null) {
            _state.update { it.copy(nameError = nameError, createFailed = false) }
            return
        }
        _state.update { it.copy(isCreating = true, createFailed = false) }
        try {
            // Returns at once: the write is queued locally and synced when online.
            shops.createShop(ownerUid, ShopNameRules.normalize(form.name), onRejected = { onCreateRejected() })
        } catch (e: Exception) {
            _state.update { it.copy(isCreating = false, createFailed = true) }
        }
    }

    /** Called by the repository when the server refuses the shop after it was queued. */
    private fun onCreateRejected() {
        _state.update { it.copy(isCreating = false, createFailed = true) }
    }
}
