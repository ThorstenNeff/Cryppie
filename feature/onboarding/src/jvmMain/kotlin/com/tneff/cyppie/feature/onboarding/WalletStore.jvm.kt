package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.WalletKeyException
import java.io.IOException

/** KAN-89/KAN-95 dedupe: persist to the shared `CiphertextStore.defaultFile()` (Desktop default path). */
actual class WalletStore {
    actual suspend fun persist(words: List<String>, password: String): WalletSetupOutcome {
        val pw = password.toCharArray()
        var seed: ByteArray? = null
        return try {
            seed = Mnemonic.of(words).toSeed()
            SeedVault(CiphertextStore.defaultFile()).store(seed, pw)
            WalletSetupOutcome.Success
        } catch (e: StorageException.KeyStoreUnavailable) {
            WalletSetupOutcome.KeystoreError
        } catch (e: IOException) {
            WalletSetupOutcome.StorageFull
        } catch (e: WalletKeyException) {
            WalletSetupOutcome.EncryptionError
        } catch (e: Throwable) {
            WalletSetupOutcome.EncryptionError
        } finally {
            pw.fill(' ')
            seed?.fill(0)
        }
    }
}

@Composable
actual fun rememberWalletStore(): WalletStore = remember { WalletStore() }
