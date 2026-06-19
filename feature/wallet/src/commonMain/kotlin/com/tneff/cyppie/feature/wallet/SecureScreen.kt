package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable

/**
 * Marks the current screen as secret while composed (blocks screenshots / hides it from the app
 * switcher) — the Send flow handles balances/addresses/the about-to-sign tx (KAN-110 §Security, like
 * Unlock/Onboarding). Android sets `FLAG_SECURE` for the screen's lifetime; iOS/Desktop are no-ops for
 * now (iOS app-switcher overlay = KAN-74). `expect`/`actual` per ADR-0003. Mirrors onboarding's seam
 * (kept module-local rather than depending on `:feature:onboarding`).
 */
@Composable
expect fun SecureScreenEffect()
