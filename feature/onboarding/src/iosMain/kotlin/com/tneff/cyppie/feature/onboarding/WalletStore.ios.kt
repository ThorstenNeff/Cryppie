@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.WalletKeyException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.numberWithBool
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual class WalletStore {
    actual suspend fun persist(words: List<String>, password: String): WalletSetupOutcome {
        val pw = password.toCharArray()
        var seed: ByteArray? = null
        return try {
            seed = Mnemonic.of(words).toSeed()
            SeedVault(IosFileCiphertextStore()).store(seed, pw)
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

/**
 * Stores the encrypted blob under **Application Support** (app-private, not Documents/iCloud) and
 * excludes the file from iCloud/iTunes backup (defense-in-depth — the KEK is password-derived).
 */
private class IosFileCiphertextStore : CiphertextStore {

    private fun filePath(): String {
        val fm = NSFileManager.defaultManager
        val base = fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        val dir = base?.URLByAppendingPathComponent("wallet")
        if (dir != null) {
            fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        return dir?.URLByAppendingPathComponent("wallet.seed")?.path.orEmpty()
    }

    override suspend fun read(): ByteArray? {
        val path = filePath().ifEmpty { return null }
        return (NSData.dataWithContentsOfFile(path))?.toByteArray()
    }

    override suspend fun write(blob: ByteArray) {
        val path = filePath()
        blob.toNSData().writeToFile(path, atomically = true)
        // Exclude from backup.
        NSURL.fileURLWithPath(path).setResourceValue(
            value = NSNumber.numberWithBool(true),
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        )
    }

    override suspend fun clear() {
        val path = filePath().ifEmpty { return }
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
    }

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}

@Composable
actual fun rememberWalletStore(): WalletStore = remember { WalletStore() }
