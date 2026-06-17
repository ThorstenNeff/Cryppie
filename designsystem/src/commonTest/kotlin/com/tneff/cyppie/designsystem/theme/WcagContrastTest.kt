package com.tneff.cyppie.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AK2 (KAN-7) — WCAG-AA contrast for the semantic colour tokens in light **and** dark.
 *
 * Pure, deterministic and multiplatform (`commonTest`, no device): we recompute the WCAG 2.x
 * relative-luminance contrast ratio for every documented foreground/background token pair from
 * [CryptasaLight]/[CryptasaDark] and assert the threshold for its role:
 *  - **text** (body / labels): ≥ 4.5:1 (WCAG 1.4.3 AA)
 *  - **non-text UI** (focus ring, brand surface boundary): ≥ 3.0:1 (WCAG 1.4.11)
 *
 * Pairs mirror the documented usage in `../Cryptasa/HANDOFF_Onboarding_Android.md` §2.1/§2.4.
 * If a pair drops below its threshold this fails loudly with the measured ratio — a real
 * a11y regression in the tokens, not a test artefact.
 */
class WcagContrastTest {

    /** WCAG relative luminance of an sRGB [Color] (its components are already sRGB 0..1). */
    private fun luminance(c: Color): Double {
        fun lin(channel: Float): Double {
            val s = channel.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    /** WCAG contrast ratio in [1.0, 21.0]; order-independent. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private data class Pair(
        val name: String,
        val fg: (CryptasaColors) -> Color,
        val bg: (CryptasaColors) -> Color,
        val min: Double,
    )

    private val textPairs = listOf(
        Pair("onSurface / surface", { it.onSurface }, { it.surface }, 4.5),
        Pair("onSurfaceVariant / surface", { it.onSurfaceVariant }, { it.surface }, 4.5),
        Pair("onSurface / surfaceVariant", { it.onSurface }, { it.surfaceVariant }, 4.5),
        Pair("onPrimary / primary", { it.onPrimary }, { it.primary }, 4.5),
        Pair("danger / dangerSurface", { it.danger }, { it.dangerSurface }, 4.5),
    )

    private val uiPairs = listOf(
        // Focus indicator and brand colour used as non-text UI affordances (WCAG 1.4.11 → 3:1).
        Pair("outlineStrong / surface", { it.outlineStrong }, { it.surface }, 3.0),
        Pair("primary / surface", { it.primary }, { it.surface }, 3.0),
    )

    private fun check(mode: String, colors: CryptasaColors, pairs: List<Pair>) {
        for (p in pairs) {
            val ratio = contrast(p.fg(colors), p.bg(colors))
            assertTrue(
                ratio >= p.min,
                "[$mode] ${p.name} contrast ${formatRatio(ratio)}:1 < required ${p.min}:1",
            )
        }
    }

    private fun formatRatio(r: Double): String {
        val rounded = (r * 100).toInt() / 100.0
        return rounded.toString()
    }

    @Test
    fun lightModeTextPairsMeetAA() = check("Light", CryptasaLight, textPairs)

    @Test
    fun darkModeTextPairsMeetAA() = check("Dark", CryptasaDark, textPairs)

    @Test
    fun lightModeUiPairsMeetNonTextContrast() = check("Light", CryptasaLight, uiPairs)

    @Test
    fun darkModeUiPairsMeetNonTextContrast() = check("Dark", CryptasaDark, uiPairs)
}
