package com.shopfinance.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B5E20)
private val GreenLight = Color(0xFF81C784)

private val LightColors = lightColorScheme(primary = Green, secondary = Color(0xFF8D6E63))
private val DarkColors = darkColorScheme(primary = GreenLight, secondary = Color(0xFFBCAAA4))

@Composable
fun ShopFinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
