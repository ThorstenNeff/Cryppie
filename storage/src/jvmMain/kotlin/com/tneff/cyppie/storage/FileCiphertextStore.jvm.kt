package com.tneff.cyppie.storage

import java.io.File

internal actual fun createFileCiphertextStore(path: String): CiphertextStore = JvmFileCiphertextStore(File(path))

/** Desktop default: app-private dir under the user home (`~/.cyppie/wallet.seed`). */
actual fun defaultSeedFilePath(): String =
    File(File(System.getProperty("user.home"), ".cyppie"), "wallet.seed").path

/** App-private file store; writes via a temp file + rename for atomicity. The blob is already AES-GCM. */
internal class JvmFileCiphertextStore(private val file: File) : CiphertextStore {
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
