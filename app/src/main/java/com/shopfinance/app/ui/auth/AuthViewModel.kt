package com.shopfinance.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shopfinance.app.data.auth.AuthOutcome
import com.shopfinance.app.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The sign-in / sign-up form. Only one request runs at a time: [submit] and
 * [sendPasswordReset] do nothing while [AuthUiState.isSubmitting] is true.
 *
 * A successful sign-in or sign-up does not navigate: the session follows
 * [AuthRepository.currentUser] by itself. The form is only reset (back to sign-in, email
 * kept, password cleared) so it starts clean if the person signs out later, because this
 * view model outlives the screen.
 */
class AuthViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) {
        _state.update {
            it.copy(name = value, fieldErrors = it.fieldErrors.copy(name = null), error = null)
        }
    }

    fun onEmailChange(value: String) {
        _state.update {
            it.copy(
                email = value,
                fieldErrors = it.fieldErrors.copy(email = null),
                error = null,
                resetEmailSent = false,
            )
        }
    }

    fun onPasswordChange(value: String) {
        _state.update {
            it.copy(password = value, fieldErrors = it.fieldErrors.copy(password = null), error = null)
        }
    }

    /** Switches between signing in and creating an account. Typed values are kept. */
    fun toggleMode() {
        _state.update {
            if (it.isSubmitting) {
                it
            } else {
                it.copy(
                    mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
                    fieldErrors = AuthFieldErrors(),
                    error = null,
                    resetEmailSent = false,
                )
            }
        }
    }

    fun submit() {
        val form = _state.value
        if (form.isSubmitting) return
        val fieldErrors = when (form.mode) {
            AuthMode.SIGN_IN -> AuthValidation.validateSignIn(form.email, form.password)
            AuthMode.SIGN_UP -> AuthValidation.validateSignUp(form.name, form.email, form.password)
        }
        if (fieldErrors.hasAny) {
            _state.update { it.copy(fieldErrors = fieldErrors, error = null, resetEmailSent = false) }
            return
        }
        _state.update {
            it.copy(fieldErrors = AuthFieldErrors(), isSubmitting = true, error = null, resetEmailSent = false)
        }
        viewModelScope.launch {
            val outcome = unlockingOnCancel {
                when (form.mode) {
                    AuthMode.SIGN_IN -> auth.signIn(form.email.trim(), form.password)
                    AuthMode.SIGN_UP -> auth.signUp(form.name.trim(), form.email.trim(), form.password)
                }
            }
            _state.update {
                when (outcome) {
                    AuthOutcome.Success -> it.copy(
                        mode = AuthMode.SIGN_IN,
                        name = "",
                        password = "",
                        isSubmitting = false,
                    )
                    is AuthOutcome.Failure -> it.copy(isSubmitting = false, error = outcome.error)
                }
            }
        }
    }

    /** Asks Firebase to email a reset link to the typed address. */
    fun sendPasswordReset() {
        val form = _state.value
        if (form.isSubmitting) return
        val emailError = AuthValidation.validateEmail(form.email)
        if (emailError != null) {
            _state.update {
                it.copy(fieldErrors = AuthFieldErrors(email = emailError), error = null, resetEmailSent = false)
            }
            return
        }
        _state.update {
            it.copy(fieldErrors = AuthFieldErrors(), isSubmitting = true, error = null, resetEmailSent = false)
        }
        viewModelScope.launch {
            val outcome = unlockingOnCancel { auth.sendPasswordReset(form.email.trim()) }
            _state.update {
                when (outcome) {
                    AuthOutcome.Success -> it.copy(isSubmitting = false, resetEmailSent = true)
                    is AuthOutcome.Failure -> it.copy(isSubmitting = false, error = outcome.error)
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Runs a repository call. The repository rethrows cancellation (for example when Firebase
     * cancels its Task), which would end the launch before [AuthUiState.isSubmitting] is
     * cleared and lock the form, so it is cleared here before the cancellation goes on.
     */
    private suspend fun unlockingOnCancel(call: suspend () -> AuthOutcome): AuthOutcome =
        try {
            call()
        } catch (e: CancellationException) {
            _state.update { it.copy(isSubmitting = false) }
            throw e
        }
}
