package com.shopfinance.app.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopfinance.app.R
import com.shopfinance.app.data.auth.AuthError
import com.shopfinance.app.ui.common.ErrorBanner
import com.shopfinance.app.ui.common.FormScreen
import com.shopfinance.app.ui.common.LoadingButton
import com.shopfinance.app.ui.common.supportingTextOrNull
import com.shopfinance.app.ui.theme.ShopFinanceTheme

/** Sign in, create an account, or ask for a password reset email. */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onToggleMode = viewModel::toggleMode,
        onForgotPassword = viewModel::sendPasswordReset,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

@Composable
private fun AuthContent(
    state: AuthUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSignUp = state.mode == AuthMode.SIGN_UP
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    // Emails and passwords are Latin text: keep them left to right inside the Arabic layout.
    val ltrTextStyle = LocalTextStyle.current.merge(TextStyle(textDirection = TextDirection.Ltr))

    FormScreen(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(if (isSignUp) R.string.auth_sign_up_title else R.string.auth_sign_in_title),
            style = MaterialTheme.typography.titleLarge,
        )

        if (isSignUp) {
            val nameError = state.fieldErrors.name?.let { displayNameErrorMessage(it) }
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.auth_name_label)) },
                isError = nameError != null,
                supportingText = supportingTextOrNull(nameError),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val emailError = state.fieldErrors.email?.let { emailErrorMessage(it) }
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.auth_email_label)) },
            isError = emailError != null,
            supportingText = supportingTextOrNull(emailError),
            textStyle = ltrTextStyle,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val passwordError = state.fieldErrors.password?.let { passwordErrorMessage(it) }
        val passwordHint = if (isSignUp) {
            stringResource(R.string.auth_password_hint, AuthValidation.MIN_PASSWORD_LENGTH)
        } else {
            null
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.auth_password_label)) },
            isError = passwordError != null,
            supportingText = supportingTextOrNull(passwordError ?: passwordHint),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        stringResource(
                            if (passwordVisible) R.string.auth_password_hide else R.string.auth_password_show,
                        ),
                    )
                }
            },
            textStyle = ltrTextStyle,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { error ->
            ErrorBanner(message = authErrorMessage(error), onDismiss = onDismissError)
        }
        if (state.resetEmailSent) {
            Text(
                text = stringResource(R.string.auth_reset_email_sent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LoadingButton(
            text = stringResource(if (isSignUp) R.string.auth_sign_up_button else R.string.auth_sign_in_button),
            loading = state.isSubmitting,
            onClick = onSubmit,
        )
        if (!isSignUp) {
            TextButton(
                onClick = onForgotPassword,
                enabled = !state.isSubmitting,
            ) {
                Text(stringResource(R.string.auth_forgot_password))
            }
        }
        TextButton(
            onClick = onToggleMode,
            enabled = !state.isSubmitting,
        ) {
            Text(
                stringResource(if (isSignUp) R.string.auth_switch_to_sign_in else R.string.auth_switch_to_sign_up),
            )
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun AuthSignInPreview() {
    ShopFinanceTheme {
        AuthContent(
            state = AuthUiState(email = "owner@example.com", error = AuthError.INVALID_CREDENTIALS),
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onToggleMode = {},
            onForgotPassword = {},
            onDismissError = {},
        )
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun AuthSignUpPreview() {
    ShopFinanceTheme {
        AuthContent(
            state = AuthUiState(
                mode = AuthMode.SIGN_UP,
                fieldErrors = AuthFieldErrors(email = EmailError.INVALID),
                email = "owner@",
            ),
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onToggleMode = {},
            onForgotPassword = {},
            onDismissError = {},
        )
    }
}
