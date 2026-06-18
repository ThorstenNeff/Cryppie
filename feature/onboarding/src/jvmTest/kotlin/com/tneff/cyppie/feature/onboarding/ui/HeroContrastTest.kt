package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.ui.graphics.Color
import com.tneff.cyppie.designsystem.theme.CryptasaBrandGradient
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * KAN-9 AK3 — WCAG-AA contrast of the **white hero text** over the brand gradient + scrim.
 *
 * `WelcomeScreen` draws white text ([onPrimary]) over [CryptasaBrandGradient] (green `#23E33E` →
 * blue `#0052FF`) with a **full-width vertical scrim band**: `verticalGradient(0→transparent,
 * 0.30→black α0.66, 0.70→black α0.66, 1→transparent)`. The wordmark + tagline are centred (~50 %
 * height), i.e. solidly inside the constant-α0.66 band and full-width, so the effective background
 * is `0.66·black over gradient` everywhere under the text — no radial falloff to model.
 *
 * We assert against the **worst** (lightest) gradient colour the text can sit over — the green end,
 * only ~1.73:1 unscrimmed — that the band lifts white text to AA: tagline (body) ≥ 4.5:1 and
 * wordmark (large) ≥ 3:1, matching the screen's design intent.
 */
class HeroContrastTest {

    /** Constant scrim opacity across the hero's vertical mid-band (WelcomeScreen). */
    private val scrimBand = 0.66f

    private fun luminance(c: Color): Double {
        fun lin(ch: Float): Double {
            val s = ch.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Black scrim of opacity [alpha] over [c] → each channel scaled by (1-alpha). */
    private fun blackScrim(c: Color, alpha: Float) = Color(
        red = c.red * (1 - alpha),
        green = c.green * (1 - alpha),
        blue = c.blue * (1 - alpha),
    )

    private val white = Color.White

    /** Lightest gradient colour the hero text can sit over — the green end (worst case). */
    private val worstGradient = CryptasaBrandGradient.first()

    /** Effective background behind the centred hero text: the scrim band over the worst colour. */
    private val effectiveBehindText = blackScrim(worstGradient, scrimBand)

    @Test
    fun rawGreenGradientEndIsSubAa_motivatesScrim() {
        // Documents *why* the scrim band exists: white on the bare light-green end is far below AA.
        val ratio = contrast(white, worstGradient)
        assertTrue(ratio < 4.5, "Expected the unscrimmed green end to be sub-AA, was $ratio")
    }

    @Test
    fun taglineBodyTextMeetsAa() {
        val ratio = contrast(white, effectiveBehindText)
        assertTrue(ratio >= 4.5, "Hero tagline (body) must meet WCAG-AA 4.5:1 over the worst gradient " +
            "colour with the α=$scrimBand band, was $ratio")
    }

    @Test
    fun wordmarkLargeTextMeetsAa() {
        val ratio = contrast(white, effectiveBehindText)
        assertTrue(ratio >= 3.0, "Hero wordmark (large text) must meet WCAG-AA-large 3:1, was $ratio")
    }
}
