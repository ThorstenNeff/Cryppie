package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable

/**
 * Marks the current screen as secret while it is composed: blocks screenshots / hides it from the
 * app switcher (§5.3 — seed/backup/password screens). Android sets `FLAG_SECURE` for the screen's
 * lifetime; other targets are no-ops for now (iOS app-switcher overlay is a follow-up; Desktop/Web
 * have no equivalent and Web never shows secrets). `expect`/`actual` per ADR-0003.
 */
@Composable
expect fun SecureScreenEffect()
