package com.tneff.cyppie.storage

/**
 * Persistence seam for the single encrypted-seed blob. Kept persistence-agnostic: the app supplies
 * the backing store (file, multiplatform-settings, …); `:storage` only handles the crypto. The blob
 * is already encrypted (AES-GCM) — this never sees plaintext.
 */
interface CiphertextStore {
    suspend fun read(): ByteArray?
    suspend fun write(blob: ByteArray)
    suspend fun clear()

    companion object {
        /** Non-persistent in-memory store — for tests and transient flows. */
        fun inMemory(): CiphertextStore = InMemoryCiphertextStore()

        /** App-private file-backed store at [path] (atomic writes; the blob is already AES-GCM). */
        fun file(path: String): CiphertextStore = createFileCiphertextStore(path)

        /**
         * File store at the platform **default seed path** ([defaultSeedFilePath]) — the single source
         * of truth for where the encrypted seed lives, so launch / unlock / onboarding all read the
         * same file (KAN-95).
         */
        fun defaultFile(): CiphertextStore = file(defaultSeedFilePath())
    }
}

/** Platform file store: java.io on jvm/android, `NSFileManager` (Application Support) on iOS. */
internal expect fun createFileCiphertextStore(path: String): CiphertextStore

/**
 * The canonical app-private path for the encrypted-seed file. Android: app `filesDir` (provide it via
 * `AndroidStoragePaths.init` at startup); iOS: Application Support, **excluded from backup**; Desktop:
 * `~/.cyppie`. Same location ONB-8 writes, so the wallet has one seed file (KAN-95).
 */
expect fun defaultSeedFilePath(): String

internal class InMemoryCiphertextStore : CiphertextStore {
    private var blob: ByteArray? = null
    override suspend fun read(): ByteArray? = blob?.copyOf()
    override suspend fun write(blob: ByteArray) {
        this.blob = blob.copyOf()
    }
    override suspend fun clear() {
        blob = null
    }
}
