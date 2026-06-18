package com.tneff.cyppie

/**
 * Whether an encrypted wallet seed is already stored on this device (returning user → unlock; KAN-89).
 * Non-web seam: android/ios/jvm read the shared `:storage` seed file (`CiphertextStore.defaultFile()`,
 * KAN-95 — the same file onboarding's `WalletStore` writes); js/wasm are onboarding/read-only → false.
 */
internal expect suspend fun walletExists(): Boolean
