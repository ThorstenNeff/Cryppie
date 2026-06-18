package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Web is read-only — no seed persistence (:storage/:wallet have no js/wasm; ADR-0008/0009).
actual class WalletStore {
    actual suspend fun persist(words: List<String>, password: String): WalletSetupOutcome =
        WalletSetupOutcome.Unsupported
}

@Composable
actual fun rememberWalletStore(): WalletStore = remember { WalletStore() }
