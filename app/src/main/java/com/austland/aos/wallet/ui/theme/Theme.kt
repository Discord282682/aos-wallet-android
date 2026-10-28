package com.austland.aos.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Kingdom of Austland • Estbrugh Treasury ("Au5" 5 Ounces Stralen) Royal Gold & Emerald palette
val RoyalGold = Color(0xFFFBBF24)
val EmeraldMint = Color(0xFF34D399)
val BgDark = Color(0xFF0B0F19)
val SurfaceDark = Color(0xFF131B2E)
val SurfaceDarkAlt = Color(0xFF1E293B)
val ErrorRed = Color(0xFFFF6B6B)

private val AosColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = Color(0xFF1C1300),
    secondary = EmeraldMint,
    onSecondary = Color(0xFF002116),
    background = BgDark,
    onBackground = Color(0xFFF1F5F9),
    surface = SurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SurfaceDarkAlt,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = ErrorRed,
    onError = Color(0xFF2B0000)
)

@Composable
fun AosWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AosColorScheme, content = content)
}
