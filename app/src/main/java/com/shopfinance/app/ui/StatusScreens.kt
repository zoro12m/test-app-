package com.shopfinance.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.shopfinance.app.R
import com.shopfinance.app.ui.common.StatusScreen
import com.shopfinance.app.ui.theme.ShopFinanceTheme

/** A centred spinner with [message], and an optional smaller [hint] under it. */
@Composable
internal fun LoadingScreen(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    StatusScreen(modifier = modifier) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Reading the shops failed. [accessDenied] means Firestore refused the read, which in
 * practice means the security rules were not published, so it gets its own message.
 */
@Composable
internal fun ShopsErrorScreen(
    accessDenied: Boolean,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusScreen(modifier = modifier) {
        Text(
            text = stringResource(R.string.shops_error_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (accessDenied) R.string.shops_error_access_denied else R.string.shops_error_generic,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
        TextButton(onClick = onSignOut) {
            Text(stringResource(R.string.action_sign_out))
        }
    }
}

/** Shown instead of the app when the build has no Firebase config, so it explains instead of crashing. */
@Composable
internal fun FirebaseMissingScreen(modifier: Modifier = Modifier) {
    StatusScreen(modifier = modifier) {
        Text(
            text = stringResource(R.string.firebase_missing_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.firebase_missing_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun ShopsErrorPreview() {
    ShopFinanceTheme {
        ShopsErrorScreen(accessDenied = true, onRetry = {}, onSignOut = {})
    }
}
