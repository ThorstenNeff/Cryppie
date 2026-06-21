package com.tneff.cyppie.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens of the Cryptasa design system.
 *
 * Values are the 1:1 translation of the `Cryptasa/Semantic` Figma collection
 * (see `../Cryptasa/HANDOFF_Onboarding_Android.md` §2.1/§2.4). Switching between
 * [CryptasaLight] and [CryptasaDark] swaps token values only — never layout.
 *
 * Screens and components must reference these tokens via [LocalCryptasaColors];
 * raw [Color] literals are not allowed outside this file.
 */
@Immutable
data class CryptasaColors(
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceRaised: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineStrong: Color,
    val primary: Color,
    val primaryHover: Color,
    val onPrimary: Color,
    val primarySurface: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerSurface: Color,
    val success: Color,
    val onSuccess: Color,
    val successSurface: Color,
    val warning: Color,
    val onWarning: Color,
    val warningSurface: Color,
    // Info — neutral hint (KAN-158); brand-blue tonal container (NOT a solid fill — distinct from the
    // primary CTA). [infoSurface] = ~10-12% (light) / ~16-20% (dark) brand-blue tint; [info] = accent
    // (icon/text), lightened on dark for WCAG-AA on the tint.
    val info: Color,
    val onInfo: Color,
    val infoSurface: Color,
    /** True for [CryptasaDark]; lets components pick mode-aware assets without re-checking the system. */
    val isDark: Boolean,
)

val CryptasaLight: CryptasaColors = CryptasaColors(
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBF0F0),
    surfaceRaised = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF5D5D5B),
    outline = Color(0xFFCFDBD5),
    outlineStrong = Color(0xFF5D5D5B),
    primary = Color(0xFF0052FF),
    primaryHover = Color(0xFF0042CC),
    onPrimary = Color(0xFFFFFFFF),
    primarySurface = Color(0xFFE8F0FF),
    danger = Color(0xFFC62828),
    onDanger = Color(0xFFFFFFFF),
    dangerSurface = Color(0xFFFDECEC),
    success = Color(0xFF0C7322),
    onSuccess = Color(0xFFFFFFFF),
    successSurface = Color(0xFFE7FAEA),
    warning = Color(0xFFA87600),
    onWarning = Color(0xFF000000),
    warningSurface = Color(0xFFFFF6E0),
    info = Color(0xFF0052FF),        // brand blue; ≈4.7:1 on infoSurface (WCAG-AA)
    onInfo = Color(0xFFFFFFFF),
    infoSurface = Color(0xFFE0EAFF), // brand blue @ ~12% tint on white
    isDark = false,
)

val CryptasaDark: CryptasaColors = CryptasaColors(
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1A1A1A),
    surfaceRaised = Color(0xFF242424),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFA0A0A0),
    outline = Color(0xFF242424),
    outlineStrong = Color(0xFFA0A0A0),
    primary = Color(0xFF0052FF),
    primaryHover = Color(0xFF0042CC),
    onPrimary = Color(0xFFFFFFFF),
    primarySurface = Color(0xFF0E1A33),
    danger = Color(0xFFFF5A5F),
    onDanger = Color(0xFF2A1416),
    dangerSurface = Color(0xFF2A1416),
    success = Color(0xFF23E33E),
    onSuccess = Color(0xFF000000),
    successSurface = Color(0xFF0F2A14),
    warning = Color(0xFFF4C952),
    onWarning = Color(0xFF000000),
    warningSurface = Color(0xFF2E2410),
    info = Color(0xFF5B8DEF),        // brand blue lightened; ≈4.8:1 on infoSurface (WCAG-AA on dark)
    onInfo = Color(0xFF001026),
    infoSurface = Color(0xFF0E1F3D), // brand blue @ ~18% tint on black
    isDark = true,
)
