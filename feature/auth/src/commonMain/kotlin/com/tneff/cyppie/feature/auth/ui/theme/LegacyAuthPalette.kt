package com.tneff.cyppie.feature.auth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Legacy colour palette of the **KAN-1 example login/registration screens** (exchange-style
 * account auth). These screens are **deferred to PRD-08** and are not part of the wallet onboarding
 * flow — they are kept only so the module keeps compiling as the feature-module pattern reference.
 *
 * The single, central theme now lives in `:designsystem`
 * ([com.tneff.cyppie.designsystem.theme.CryptasaTheme], ADR-0004). Do **not** add new colours here
 * or use these in onboarding — use the `CryptasaTheme` tokens instead. When PRD-08 reworks account
 * auth, these screens should migrate onto the central design system and this file should be deleted.
 */
val CryptasaBlue = Color(0xFF0052FF)
val CryptasaGreen = Color(0xFF79FF9E)
val TextPrimary = Color(0xFF000000)
val TextSubtitle = Color(0xFF5D5D5B)
val SoftWhite = Color(0x80FFFFFF)
val DividerGray = Color(0xFFEDEDED)
val FieldBorder = Color(0x99CFDBD5) // #CFDBD5 @ 60% — form field outlines
