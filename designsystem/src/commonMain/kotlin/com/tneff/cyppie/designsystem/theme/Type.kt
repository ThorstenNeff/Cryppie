package com.tneff.cyppie.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography tokens (HANDOFF §2.3). The kit uses **Inter**; until the Inter font files are
 * bundled as a `composeResources` asset (UI/UX deliverable), [CryptasaFontFamily] is the single
 * swap point — replace it with the Inter [FontFamily] and every style below picks it up.
 *
 * Onboarding ramp: title Bold 24–26, body Regular 14–15, button/label Semi&nbsp;Bold 16, helper 12.
 */
val CryptasaFontFamily: FontFamily = FontFamily.Default

@Immutable
data class CryptasaTypography(
    val titleLarge: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp,
    ),
    val titleSmall: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp,
    ),
    val helper: TextStyle = TextStyle(
        fontFamily = CryptasaFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp,
    ),
)

val CryptasaTypographyTokens: CryptasaTypography = CryptasaTypography()
