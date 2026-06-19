package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.tneff.cyppie.designsystem.SecureSnapshotGuard

/**
 * iOS app-switcher snapshot protection for the Send flow (KAN-74, ADR-0005) — delegates to the shared
 * [SecureSnapshotGuard] (one ref-count across onboarding seed/unlock + wallet Send). Replaces the no-op.
 */
@Composable
actual fun SecureScreenEffect() {
    DisposableEffect(Unit) {
        SecureSnapshotGuard.acquire()
        onDispose { SecureSnapshotGuard.release() }
    }
}
