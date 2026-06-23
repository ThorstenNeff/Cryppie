package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import com.tneff.cyppie.evm.network.NetworkPreferenceStore

/**
 * KAN-173 — the platform-backed [NetworkPreferenceStore] (the active-network selection persists across app
 * restarts, AK). Plain preference (not a secret): Android `SharedPreferences`, iOS `NSUserDefaults`, desktop
 * `java.util.prefs`. Mirrors the `rememberWalletStore` factory pattern so the android actual reaches the
 * `LocalContext`. Wired into the shell's `ActiveNetworkStore` so a switch survives process death (the store
 * persists-then-emits; loading the persisted env at construction restores it).
 */
@Composable
internal expect fun rememberNetworkPreferenceStore(): NetworkPreferenceStore
