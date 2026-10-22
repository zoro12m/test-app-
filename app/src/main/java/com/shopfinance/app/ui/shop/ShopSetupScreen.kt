package com.shopfinance.app.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopfinance.app.R
import com.shopfinance.app.data.shop.ShopNameError
import com.shopfinance.app.data.shop.ShopNameRules
import com.shopfinance.app.ui.common.FormScreen
import com.shopfinance.app.ui.common.LoadingButton
import com.shopfinance.app.ui.common.supportingTextOrNull
import com.shopfinance.app.ui.theme.ShopFinanceTheme

/**
 * Shown once, after the first sign-in: names the person's shop. The currency is fixed to
 * USD and only shown, never chosen. [onSignOut] lets someone who signed in with the wrong
 * account leave.
 */
@Composable
fun ShopSetupScreen(
    viewModel: ShopSetupViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShopSetupContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onCreate = viewModel::submit,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

@Composable
private fun ShopSetupContent(
    state: ShopSetupUiState,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormScreen(modifier = modifier) {
        Text(
            text = stringResource(R.string.shop_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.shop_setup_body),
            style = MaterialTheme.typography.bodyLarge,
        )

        val nameError = state.nameError?.let { shopNameErrorMessage(it) }
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.shop_name_label)) },
            placeholder = { Text(stringResource(R.string.shop_name_placeholder)) },
            isError = nameError != null,
            supportingText = supportingTextOrNull(nameError),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onCreate() }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.shop_currency_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.currency_usd),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.shop_currency_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.createFailed) {
            Text(
                text = stringResource(R.string.shop_create_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        LoadingButton(
            text = stringResource(R.string.shop_create_button),
            loading = state.isCreating,
            onClick = onCreate,
        )
        TextButton(onClick = onSignOut) {
            Text(stringResource(R.string.action_sign_out))
        }
    }
}

@Composable
private fun shopNameErrorMessage(error: ShopNameError): String = when (error) {
    ShopNameError.EMPTY -> stringResource(R.string.shop_name_empty)
    ShopNameError.TOO_LONG -> stringResource(R.string.shop_name_too_long, ShopNameRules.MAX_LENGTH)
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun ShopSetupPreview() {
    ShopFinanceTheme {
        ShopSetupContent(
            state = ShopSetupUiState(name = "", nameError = ShopNameError.EMPTY),
            onNameChange = {},
            onCreate = {},
            onSignOut = {},
        )
    }
}
