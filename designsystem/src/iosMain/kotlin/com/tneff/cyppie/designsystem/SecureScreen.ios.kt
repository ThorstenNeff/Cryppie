package com.tneff.cyppie.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * iOS app-switcher snapshot protection (KAN-74, ADR-0005) — delegates to the shared [SecureSnapshotGuard]
 * (one ref-count across every secure screen: onboarding seed/unlock, wallet Send, Copy/Strat sign).
 */
@Composable
actual fun SecureScreenEffect() {
    DisposableEffect(Unit) {
        SecureSnapshotGuard.acquire()
        onDispose { SecureSnapshotGuard.release() }
    }
}
