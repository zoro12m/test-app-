package com.shopfinance.app.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shopfinance.app.R
import com.shopfinance.app.data.auth.AuthError

/** The message shown for a failure reported by Firebase. */
@Composable
internal fun authErrorMessage(error: AuthError): String = stringResource(
    when (error) {
        AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
        AuthError.INVALID_EMAIL -> R.string.auth_error_invalid_email
        AuthError.EMAIL_IN_USE -> R.string.auth_error_email_in_use
        AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
        AuthError.USER_DISABLED -> R.string.auth_error_user_disabled
        AuthError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many_requests
        AuthError.NETWORK -> R.string.auth_error_network
        AuthError.UNKNOWN -> R.string.auth_error_unknown
    },
)

@Composable
internal fun displayNameErrorMessage(error: DisplayNameError): String = when (error) {
    DisplayNameError.EMPTY -> stringResource(R.string.auth_name_empty)
    DisplayNameError.TOO_LONG -> stringResource(R.string.auth_name_too_long, AuthValidation.MAX_NAME_LENGTH)
}

@Composable
internal fun emailErrorMessage(error: EmailError): String = when (error) {
    EmailError.EMPTY -> stringResource(R.string.auth_email_empty)
    EmailError.INVALID -> stringResource(R.string.auth_email_invalid)
}

@Composable
internal fun passwordErrorMessage(error: PasswordError): String = when (error) {
    PasswordError.EMPTY -> stringResource(R.string.auth_password_empty)
    PasswordError.TOO_SHORT ->
        stringResource(R.string.auth_password_too_short, AuthValidation.MIN_PASSWORD_LENGTH)
}
