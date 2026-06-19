package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.tneff.cyppie.designsystem.SecureSnapshotGuard

/**
 * iOS app-switcher snapshot protection (KAN-74, ADR-0005) — replaces the former no-op. Delegates to the
 * shared [SecureSnapshotGuard] so the cover is installed while ≥1 secure screen (seed / unlock password)
 * is composed, across modules. (Screenshots can't be blocked on iOS as on Android, but the switcher /
 * Recents snapshot — the actual seed-leak vector — is covered.)
 */
@Composable
actual fun SecureScreenEffect() {
    DisposableEffect(Unit) {
        SecureSnapshotGuard.acquire()
        onDispose { SecureSnapshotGuard.release() }
    }
}
