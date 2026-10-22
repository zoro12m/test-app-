package com.shopfinance.app.ui.auth

import com.shopfinance.app.data.auth.AuthError

enum class AuthMode { SIGN_IN, SIGN_UP }

/**
 * Everything the sign-in / sign-up screen shows. [name] is only used in [AuthMode.SIGN_UP].
 * [error] is the last failure reported by Firebase; [fieldErrors] are the app's own checks.
 */
data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val fieldErrors: AuthFieldErrors = AuthFieldErrors(),
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    /** True after a password reset email was requested successfully, until the form changes. */
    val resetEmailSent: Boolean = false,
)
