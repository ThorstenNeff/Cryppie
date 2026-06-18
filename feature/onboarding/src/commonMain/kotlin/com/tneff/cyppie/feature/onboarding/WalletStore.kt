package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable

/** Outcome of persisting the wallet (ONB-8); maps to the setup screen's terminal states. */
enum class WalletSetupOutcome { Success, EncryptionError, KeystoreError, StorageFull, Unsupported }

/**
 * Onboarding's seam to the L1/`:storage` secure-seed layer (ONB-8). Like [MnemonicSupport], `:storage`
 * (+`:wallet`) has no js/wasm targets, so this is `expect`/`actual`: android/ios/jvm encrypt the seed
 * and persist it; js/wasm return [WalletSetupOutcome.Unsupported] (Web is read-only).
 *
 * The non-web `actual` derives the 64-byte BIP-39 seed from the words ([com.tneff.cyppie.wallet.Mnemonic.toSeed]),
 * then `SeedVault.store(seed, password)` (PBKDF2→AES-GCM, ADR-0009) into an app-private, **non-synced**,
 * backup-excluded file. Only the encrypted seed is stored — no account model / derived keys (PRD-02).
 */
expect class WalletStore {
    suspend fun persist(words: List<String>, password: String): WalletSetupOutcome
}

/** Provides a platform [WalletStore] (Android needs the app `Context`, hence a composable factory). */
@Composable
expect fun rememberWalletStore(): WalletStore
