package com.tneff.cyppie.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** User-selectable theme mode. Default is [System]; persisted later by settings (PRD-08). */
enum class ThemeMode { System, Light, Dark }

val LocalCryptasaColors = staticCompositionLocalOf<CryptasaColors> {
    error("CryptasaColors not provided — wrap content in CryptasaTheme { }")
}
val LocalCryptasaSpacing = staticCompositionLocalOf { CryptasaSpacing }
val LocalCryptasaRadius = staticCompositionLocalOf { CryptasaRadius }
val LocalCryptasaTypography = staticCompositionLocalOf { CryptasaTypographyTokens }

/**
 * Single, central design-system theme shared by every feature (ADR-0004). Exposes Cryptasa
 * tokens via [LocalCryptasaColors]/[LocalCryptasaSpacing]/[LocalCryptasaRadius]/[LocalCryptasaTypography]
 * and mirrors the semantic colours onto a Material 3 [androidx.compose.material3.ColorScheme] so
 * Material defaults stay on-token. Switching [mode] (or the system setting) swaps token values only.
 */
@Composable
fun CryptasaTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) CryptasaDark else CryptasaLight

    val materialColorScheme = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.surface,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            error = colors.danger,
            onError = colors.onDanger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.surface,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            error = colors.danger,
            onError = colors.onDanger,
        )
    }

    CompositionLocalProvider(
        LocalCryptasaColors provides colors,
        LocalCryptasaSpacing provides CryptasaSpacing,
        LocalCryptasaRadius provides CryptasaRadius,
        LocalCryptasaTypography provides CryptasaTypographyTokens,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography(),
            content = content,
        )
    }
}

/** Convenience accessors so call sites read `CryptasaTheme.colors.primary`, etc. */
object CryptasaTheme {
    val colors: CryptasaColors
        @Composable @ReadOnlyComposable get() = LocalCryptasaColors.current
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalCryptasaSpacing.current
    val radius: Radius
        @Composable @ReadOnlyComposable get() = LocalCryptasaRadius.current
    val typography: CryptasaTypography
        @Composable @ReadOnlyComposable get() = LocalCryptasaTypography.current
}
