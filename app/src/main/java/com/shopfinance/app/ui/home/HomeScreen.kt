package com.shopfinance.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shopfinance.app.R
import com.shopfinance.app.data.auth.AuthUser
import com.shopfinance.app.data.shop.Shop
import com.shopfinance.app.ui.common.currencyName
import com.shopfinance.app.ui.theme.ShopFinanceTheme
import com.shopfinance.domain.money.Currency

/**
 * The shop's home. Stage 2 only confirms that the shop is set up; sales and expenses
 * arrive in the next update.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: AuthUser,
    shop: Shop,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = shop.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(R.string.action_sign_out))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val name = user.displayName
                    Text(
                        text = if (name != null) {
                            stringResource(R.string.home_greeting_named, name)
                        } else {
                            stringResource(R.string.home_greeting)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.home_shop_ready, shop.name),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.home_currency, currencyName(shop.currency)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_next_update),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun HomeScreenPreview() {
    ShopFinanceTheme {
        HomeScreen(
            user = AuthUser(uid = "preview", email = "owner@example.com", displayName = "أبو علي"),
            shop = Shop(id = "preview", name = "سوبرماركت الأمل", ownerUid = "preview", currency = Currency.USD),
            onSignOut = {},
        )
    }
}
