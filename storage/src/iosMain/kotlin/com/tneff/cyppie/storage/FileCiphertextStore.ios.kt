@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tneff.cyppie.storage

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

internal actual fun createFileCiphertextStore(path: String): CiphertextStore = IosFileCiphertextStore(path)

/** iOS default: Application Support `/Cyppie/wallet.seed` (app-private, excluded from backup on write). */
actual fun defaultSeedFilePath(): String {
    val fm = NSFileManager.defaultManager
    val base = fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    val dir = base?.URLByAppendingPathComponent("Cyppie")
    if (dir != null) {
        fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }
    return dir?.URLByAppendingPathComponent("wallet.seed")?.path.orEmpty()
}

/** Stores under Application Support (not Documents/iCloud) and excludes the file from backup. */
private class IosFileCiphertextStore(private val path: String) : CiphertextStore {
    override suspend fun read(): ByteArray? =
        if (path.isEmpty()) null else NSData.dataWithContentsOfFile(path)?.toByteArray()

    override suspend fun write(blob: ByteArray) {
        // A silently-ignored write would look like success while nothing was saved — propagate failure.
        check(path.isNotEmpty()) { "Application Support directory unavailable" }
        val written = blob.toNSData().writeToFile(path, atomically = true)
        check(written) { "Failed to persist seed file" }
        NSURL.fileURLWithPath(path).setResourceValue(
            value = NSNumber.numberWithBool(true),
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        )
    }

    override suspend fun clear() {
        if (path.isNotEmpty()) NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
