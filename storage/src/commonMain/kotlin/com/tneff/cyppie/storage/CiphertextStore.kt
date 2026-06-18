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
    }
}

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
