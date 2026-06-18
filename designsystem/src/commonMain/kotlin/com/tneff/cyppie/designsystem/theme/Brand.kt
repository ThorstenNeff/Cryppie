package com.tneff.cyppie.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand gradient for the onboarding hero (HANDOFF / ONB-1): a fixed diagonal **green → blue**
 * (`success`-green → `primary`-blue). It is intentionally **mode-independent** — identical in light
 * and dark (the hero gradient does not flip). Defined here in the design system so screens reference
 * the token instead of raw colour literals (§5.2).
 */
val CryptasaBrandGradient: List<Color> = listOf(
    Color(0xFF23E33E),
    Color(0xFF0052FF),
)
