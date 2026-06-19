package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedSession
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.WalletKeyException

/**
 * KAN-89/KAN-95 dedupe: persist to the shared `CiphertextStore.defaultFile()` (iOS Application
 * Support, backup-excluded — provided by `:storage`). Replaces the per-module NSData file IO so the
 * app-shell launch check reads the same file (single source for "where the seed lives").
 */
actual class WalletStore {
    actual suspend fun persist(words: List<String>, password: String): WalletSetupOutcome {
        val pw = password.toCharArray()
        var seed: ByteArray? = null
        return try {
            seed = Mnemonic.of(words).toSeed()
            // KAN-111: persist AND open the in-memory session now (the seed was just created/imported),
            // so the app-shell lands on Home unlocked instead of bouncing the just-set password to
            // Unlock. The session owns its own copy (zeroized on lock); the local `seed` is zeroized below.
            val opened = SeedVault(CiphertextStore.defaultFile()).storeAndOpen(seed, pw)
            // N1: if installing the session fails, close the opened source so its seed copy can't leak.
            try {
                SeedSession.set(opened)
            } catch (t: Throwable) {
                opened.close()
                throw t
            }
            WalletSetupOutcome.Success
        } catch (e: StorageException.KeyStoreUnavailable) {
            WalletSetupOutcome.KeystoreError
        } catch (e: WalletKeyException) {
            WalletSetupOutcome.EncryptionError
        } catch (e: Throwable) {
            // No reliable disk-full signal here; treat persistence failures as storage errors.
            WalletSetupOutcome.StorageFull
        } finally {
            pw.fill(' ')
            seed?.fill(0)
        }
    }
}

@Composable
actual fun rememberWalletStore(): WalletStore = remember { WalletStore() }
