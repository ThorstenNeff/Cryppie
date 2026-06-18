package com.tneff.cyppie.storage

import java.io.File

internal actual fun createFileCiphertextStore(path: String): CiphertextStore = AndroidFileCiphertextStore(File(path))

/** Android default: `<filesDir>/cyppie/wallet.seed` (filesDir provided once via [AndroidStoragePaths]). */
actual fun defaultSeedFilePath(): String =
    File(File(AndroidStoragePaths.requireFilesDir(), "cyppie"), "wallet.seed").path

/**
 * The app calls [init] once at startup (e.g. `Application.onCreate`) with `filesDir.path`, so
 * `:storage` can resolve [defaultSeedFilePath] without holding a `Context` (KAN-95).
 */
object AndroidStoragePaths {
    @Volatile
    private var filesDir: String? = null

    fun init(filesDirPath: String) {
        filesDir = filesDirPath
    }

    internal fun requireFilesDir(): String =
        filesDir ?: error("AndroidStoragePaths.init(filesDir) must be called at startup before defaultSeedFilePath()")
}

/** App-private file store; writes via a temp file + rename for atomicity. The blob is already AES-GCM. */
internal class AndroidFileCiphertextStore(private val file: File) : CiphertextStore {
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
