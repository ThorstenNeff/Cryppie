package com.tneff.cyppie.feature.auth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Design tokens taken from the Cryptasa UI Kit (Figma).
val CryptasaBlue = Color(0xFF0052FF)
val CryptasaGreen = Color(0xFF79FF9E)
val TextPrimary = Color(0xFF000000)
val TextSubtitle = Color(0xFF5D5D5B)
val SoftWhite = Color(0x80FFFFFF)
val DividerGray = Color(0xFFEDEDED)

private val CryptasaColorScheme = lightColorScheme(
    primary = CryptasaBlue,
    onPrimary = Color.White,
    background = Color.White,
    surface = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val CryptasaTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
)

@Composable
fun CryptasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CryptasaColorScheme,
        typography = CryptasaTypography,
        content = content,
    )
}
