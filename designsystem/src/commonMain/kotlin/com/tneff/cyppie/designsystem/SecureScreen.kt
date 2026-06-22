package com.tneff.cyppie.designsystem

import androidx.compose.runtime.Composable

/**
 * Marks the current screen as secret while composed — blocks screenshots and hides it from the app
 * switcher — for any screen that shows a secret or a sign/authorization context (seed / password /
 * disclosure / on-device sign). Android sets `FLAG_SECURE` for the screen's lifetime (ref-counted so a
 * secure→secure navigation never net-clears during the transition); iOS installs the app-switcher snapshot
 * cover via [SecureSnapshotGuard] (KAN-74); Desktop/Web are no-ops. `expect`/`actual` per ADR-0003.
 *
 * Lives in `:designsystem` (KAN-168) so EVERY UI module wraps its OWN secure screens — the protection
 * travels with the screen, not with a host that a future caller might forget to wire. Security-sensitive
 * screens MUST call this themselves; do not rely on the navigation host.
 */
@Composable
expect fun SecureScreenEffect()
