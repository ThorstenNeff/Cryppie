package com.tneff.cyppie.storage

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.coroutines.cancellation.CancellationException

/**
 * Encrypts the BIP-39 seed at rest and hands L1 a decrypted [SecureSeedSource] on unlock (ADR-0009).
 *
 * Scheme (record format v1): a per-wallet 16-byte salt → **PBKDF2-HMAC-SHA512** (OWASP-aligned
 * iterations) derives the 256-bit KEK from the app password → **AES-GCM** encrypts the seed (the
 * `cryptography-kotlin` cipher prepends a random IV and appends the auth tag). The password is the
 * cryptographic gate; the seed is never persisted in clear. The ciphertext carries a version header
 * so a later KDF migration (→ Argon2id, v2) needs no data reset.
 *
 * **Secret handling:** the caller **owns** the input `password: CharArray` — this class does not
 * mutate or zeroize it (zeroize it yourself after the call). Internally derived secrets (the UTF-8
 * password bytes and the KEK) are zeroized here. The decrypted seed is returned inside a
 * [SecureSeedSource]; zeroize it via [SecureSeedSource.close] when locking.
 *
 * [keyStore] is the optional biometric convenience layer (ONB-9); with [NoopSecureKeyStore] the
 * wallet is password-only.
 */
class SeedVault(
    private val store: CiphertextStore,
    private val keyStore: SecureKeyStore = NoopSecureKeyStore,
) {
    private val provider = CryptographyProvider.Default

    /** True once a seed has been stored. */
    suspend fun isInitialized(): Boolean = store.read() != null

    /**
     * Encrypts [seed] under [password] and persists it, overwriting any existing record. Any prior
     * biometric entry is cleared, since it would release a now-stale password (M3 — full
     * password-change flow is a follow-up); re-call [enableBiometricUnlock] afterwards.
     */
    suspend fun store(seed: ByteArray, password: CharArray) {
        require(seed.isNotEmpty()) { "seed must not be empty" }
        val salt = CryptographyRandom.nextBytes(SALT_SIZE)
        val pwBytes = Utf8.encode(password)
        val kek = deriveKek(pwBytes, salt)
        try {
            val ciphertext = aesKey(kek).cipher().encrypt(plaintext = seed)
            store.write(encodeRecord(salt, ciphertext))
            keyStore.clear(SEED_UNLOCK_ALIAS)
        } finally {
            kek.fill(0)
            pwBytes.fill(0)
        }
    }

    /**
     * Stores [seed] under [password] (as [store]) **and** returns an open [SecureSeedSource] for it —
     * so onboarding can open the in-memory session the instant the wallet is created/imported, without
     * a second PBKDF2 unlock (KAN-111: otherwise the app-shell sees no session and bounces the just-set
     * password to Unlock). The returned source owns a private copy of the seed (zeroized on
     * [SecureSeedSource.close]); the caller still owns + zeroizes [seed] and [password].
     */
    suspend fun storeAndOpen(seed: ByteArray, password: CharArray): SecureSeedSource {
        store(seed, password)
        return SecureSeedSource(seed.copyOf())
    }

    /**
     * Decrypts the stored seed with [password] and returns a zeroize-able [SecureSeedSource] for the
     * unlocked session. Throws [StorageException.InvalidPassword] on a wrong password or tampered
     * ciphertext, [StorageException.NotInitialized] if nothing is stored,
     * [StorageException.CorruptData] if the record is structurally invalid.
     */
    suspend fun unlock(password: CharArray): SecureSeedSource {
        val pwBytes = Utf8.encode(password)
        try {
            return unlockWithPasswordBytes(pwBytes)
        } finally {
            pwBytes.fill(0)
        }
    }

    /**
     * Deletes the stored seed **and** the biometric-unlock entry. Both the seed and the keystore
     * credential live behind the shared [SEED_UNLOCK_ALIAS], so a reset can't leave a now-stale
     * password recoverable by biometrics behind a wiped seed (KAN-82).
     */
    suspend fun clear() {
        store.clear()
        keyStore.clear(SEED_UNLOCK_ALIAS)
    }

    // ---- biometric convenience (ONB-9 seam) ----

    /** Stores [password] in the platform keystore so [unlockWithBiometrics] can release it. */
    suspend fun enableBiometricUnlock(password: CharArray) {
        val pwBytes = Utf8.encode(password)
        try {
            keyStore.protect(SEED_UNLOCK_ALIAS, pwBytes)
        } finally {
            pwBytes.fill(0)
        }
    }

    /** Unlocks via biometrics (releases the stored password bytes, then decrypts). */
    suspend fun unlockWithBiometrics(): SecureSeedSource {
        val pwBytes = keyStore.retrieve(SEED_UNLOCK_ALIAS)
            ?: throw StorageException.KeyStoreUnavailable("No biometric entry available")
        try {
            return unlockWithPasswordBytes(pwBytes)
        } finally {
            pwBytes.fill(0)
        }
    }

    /** Core unlock path over already-encoded password bytes (not zeroized here — caller owns them). */
    private suspend fun unlockWithPasswordBytes(pwBytes: ByteArray): SecureSeedSource {
        val blob = store.read() ?: throw StorageException.NotInitialized()
        val (salt, ciphertext) = decodeRecord(blob)
        if (ciphertext.size < MIN_GCM_SIZE) throw StorageException.CorruptData("ciphertext too short")
        val kek = deriveKek(pwBytes, salt)
        val seed = try {
            aesKey(kek).cipher().decrypt(ciphertext = ciphertext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Throwable) {
            // AES-GCM auth failure: wrong password or tampered data. No message/cause — avoid leaks.
            throw StorageException.InvalidPassword()
        } finally {
            kek.fill(0)
        }
        return SecureSeedSource(seed)
    }

    private suspend fun deriveKek(passwordBytes: ByteArray, salt: ByteArray): ByteArray =
        provider.get(PBKDF2)
            .secretDerivation(
                digest = SHA512,
                iterations = PBKDF2_ITERATIONS,
                outputSize = KEK_SIZE_BITS.bits,
                salt = salt,
            )
            .deriveSecretToByteArray(passwordBytes)

    private suspend fun aesKey(kek: ByteArray): AES.GCM.Key =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, kek)

    /** record = [version:1][saltLen:1][salt][aes-gcm ciphertext (iv‖ct‖tag)]. */
    private fun encodeRecord(salt: ByteArray, ciphertext: ByteArray): ByteArray {
        val out = ByteArray(2 + salt.size + ciphertext.size)
        out[0] = FORMAT_VERSION
        out[1] = salt.size.toByte()
        salt.copyInto(out, 2)
        ciphertext.copyInto(out, 2 + salt.size)
        return out
    }

    private fun decodeRecord(blob: ByteArray): Pair<ByteArray, ByteArray> {
        if (blob.size < 3) throw StorageException.CorruptData("record too short")
        if (blob[0] != FORMAT_VERSION) throw StorageException.CorruptData("unknown record version ${blob[0]}")
        val saltLen = blob[1].toInt() and 0xFF
        if (blob.size < 2 + saltLen + 1) throw StorageException.CorruptData("record truncated")
        return blob.copyOfRange(2, 2 + saltLen) to blob.copyOfRange(2 + saltLen, blob.size)
    }

    private companion object {
        const val FORMAT_VERSION: Byte = 1 // v1 = PBKDF2-HMAC-SHA512 + AES-GCM (Argon2id = future v2)
        const val SALT_SIZE = 16
        const val PBKDF2_ITERATIONS = 210_000 // OWASP-aligned for SHA-512 (ADR-0009)
        const val KEK_SIZE_BITS = 256
        const val MIN_GCM_SIZE = 12 + 16 // 12-byte IV + 16-byte tag
    }
}
