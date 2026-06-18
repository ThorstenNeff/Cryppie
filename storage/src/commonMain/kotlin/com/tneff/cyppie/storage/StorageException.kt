package com.tneff.cyppie.storage

/** Sealed failures for the secure seed store (ADR-0009). Messages never contain seed/key material. */
sealed class StorageException(message: String) : Exception(message) {

    /** Decryption failed — wrong password or a tampered ciphertext (AES-GCM tag mismatch). */
    class InvalidPassword(message: String = "Wrong password or corrupted data") : StorageException(message)

    /** The stored blob is missing, truncated, or has an unknown version header. */
    class CorruptData(message: String) : StorageException(message)

    /** No encrypted seed has been stored yet. */
    class NotInitialized(message: String = "No wallet seed stored") : StorageException(message)

    /** The platform keystore/keychain is unavailable or the biometric unlock was refused. */
    class KeyStoreUnavailable(message: String) : StorageException(message)
}
