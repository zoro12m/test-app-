package com.shopfinance.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shopfinance.app.ui.AppRoot
import com.shopfinance.app.ui.theme.ShopFinanceTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ShopFinanceApplication).container
        setContent {
            ShopFinanceTheme {
                AppRoot(container = container)
            }
        }
    }
}
