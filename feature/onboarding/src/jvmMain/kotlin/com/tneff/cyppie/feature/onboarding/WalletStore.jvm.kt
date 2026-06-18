package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.WalletKeyException
import java.io.File
import java.io.IOException

actual class WalletStore(private val dir: File) {
    actual suspend fun persist(words: List<String>, password: String): WalletSetupOutcome {
        val pw = password.toCharArray()
        var seed: ByteArray? = null
        return try {
            seed = Mnemonic.of(words).toSeed()
            SeedVault(FileCiphertextStore(File(dir, SEED_FILE))).store(seed, pw)
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

    private companion object {
        const val SEED_FILE = "wallet.seed"
    }
}

private class FileCiphertextStore(private val file: File) : CiphertextStore {
    override suspend fun read(): ByteArray? = if (file.exists()) file.readBytes() else null

    override suspend fun write(blob: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(file)) {
            file.writeBytes(blob)
            tmp.delete()
        }
    }

    override suspend fun clear() {
        file.delete()
    }
}

@Composable
actual fun rememberWalletStore(): WalletStore {
    // Desktop: app-private dir under the user home.
    val dir = File(System.getProperty("user.home"), ".cyppie/wallet")
    return remember { WalletStore(dir) }
}
