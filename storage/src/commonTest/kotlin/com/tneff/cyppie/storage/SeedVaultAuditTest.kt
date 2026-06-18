package com.tneff.cyppie.storage

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * KAN-75 — **SeedVault audit gate** (ADR-0009, the most security-sensitive crypto). Complements the
 * Dev self-tests with the assistant-review gaps + an independent KDF known-answer check:
 *  - **KDF KAT:** re-derive the KEK with *exactly* PBKDF2-HMAC-SHA512 @ 210 000 / 256-bit and
 *    AES-GCM-decrypt the stored ciphertext — only succeeds if SeedVault used those exact params
 *    (any deviation → GCM tag mismatch). This pins the iteration count externally.
 *  - **Fake-hardware biometric round-trip** (Dev only covered the Noop "unavailable" path).
 *  - **Truncated / oversized `saltLen` header → CorruptData** (not IndexOOB/crash).
 *  - **Isolated salt- and IV-freshness** (separately, not conflated whole-blob compare).
 *  - **No plaintext seed in the record.**
 * Only synthetic test material — no real seeds/passwords; no plaintext in artefacts (§4.3).
 */
class SeedVaultAuditTest {

    private val seed = ByteArray(64) { (0xA0 + it).toByte() } // synthetic 64-byte seed
    private val password = "correct horse battery staple"

    /** In-memory hardware-backed keystore standing in for Android Keystore / iOS Keychain. */
    private class FakeHardwareKeyStore : SecureKeyStore {
        private val entries = mutableMapOf<String, ByteArray>()
        override val isHardwareBacked = true
        override suspend fun protect(alias: String, secret: ByteArray) { entries[alias] = secret.copyOf() }
        override suspend fun retrieve(alias: String): ByteArray? = entries[alias]?.copyOf()
        override suspend fun clear(alias: String) { entries.remove(alias) }
    }

    /** record = [version:1][saltLen:1][salt][iv‖ct‖tag] → (version, salt, ciphertext). */
    private fun decode(blob: ByteArray): Triple<Int, ByteArray, ByteArray> {
        val saltLen = blob[1].toInt() and 0xFF
        return Triple(blob[0].toInt(), blob.copyOfRange(2, 2 + saltLen), blob.copyOfRange(2 + saltLen, blob.size))
    }

    // ---- KDF KAT: pins PBKDF2-HMAC-SHA512 @ 210k externally ----

    @Test
    fun kdfIsPbkdf2HmacSha512With210kIterations() = runTest {
        val store = CiphertextStore.inMemory()
        SeedVault(store).store(seed.copyOf(), password.toCharArray())
        val (version, salt, ciphertext) = decode(store.read()!!)
        assertEquals(1, version)
        assertEquals(16, salt.size)

        val provider = CryptographyProvider.Default
        val kek = provider.get(PBKDF2).secretDerivation(
            digest = SHA512,
            iterations = 210_000,
            outputSize = 256.bits,
            salt = salt,
        ).deriveSecretToByteArray(Utf8.encode(password.toCharArray()))
        val recovered = provider.get(AES.GCM).keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, kek)
            .cipher()
            .decrypt(ciphertext = ciphertext)

        // Decrypts ⇔ SeedVault derived the KEK with exactly these params (digest, 210k, 256-bit).
        assertContentEquals(seed, recovered)
    }

    // ---- Fake-hardware biometric round-trip (gap 1) ----

    @Test
    fun biometricRoundTripWithHardwareKeyStore() = runTest {
        val vault = SeedVault(CiphertextStore.inMemory(), FakeHardwareKeyStore())
        vault.store(seed.copyOf(), password.toCharArray())
        vault.enableBiometricUnlock(password.toCharArray())
        val recovered = vault.unlockWithBiometrics().withSeed { it.copyOf() }
        assertContentEquals(seed, recovered)
    }

    @Test
    fun reStoreClearsStaleBiometricEntry() = runTest {
        // M3: a fresh store() must invalidate a biometric entry that would release the old password.
        val vault = SeedVault(CiphertextStore.inMemory(), FakeHardwareKeyStore())
        vault.store(seed.copyOf(), password.toCharArray())
        vault.enableBiometricUnlock(password.toCharArray())
        vault.store(seed.copyOf(), "a different password".toCharArray()) // rotates → clears bio entry
        assertFailsWith<StorageException.KeyStoreUnavailable> { vault.unlockWithBiometrics() }
    }

    // ---- Truncated / oversized record header → CorruptData (gap 2) ----

    @Test
    fun truncatedRecordIsCorruptData() = runTest {
        val store = CiphertextStore.inMemory()
        store.write(byteArrayOf(1, 16)) // version + saltLen, but no salt/ciphertext
        assertFailsWith<StorageException.CorruptData> { SeedVault(store).unlock(password.toCharArray()) }
    }

    @Test
    fun oversizedSaltLenHeaderIsCorruptData() = runTest {
        val store = CiphertextStore.inMemory()
        SeedVault(store).store(seed.copyOf(), password.toCharArray())
        val blob = store.read()!!
        blob[1] = 0xC8.toByte() // saltLen = 200, far beyond the record → must fail closed, not crash
        store.write(blob)
        assertFailsWith<StorageException.CorruptData> { SeedVault(store).unlock(password.toCharArray()) }
    }

    // ---- Isolated salt- and IV-freshness (gap 3) ----

    @Test
    fun saltAndIvAreFreshPerStore_isolated() = runTest {
        val a = CiphertextStore.inMemory().also { SeedVault(it).store(seed.copyOf(), password.toCharArray()) }
        val b = CiphertextStore.inMemory().also { SeedVault(it).store(seed.copyOf(), password.toCharArray()) }
        val (_, saltA, ctA) = decode(a.read()!!)
        val (_, saltB, ctB) = decode(b.read()!!)
        // Salt freshness — independent of the IV.
        assertFalse(saltA.contentEquals(saltB), "per-store salt must be random")
        // IV freshness — the AES-GCM IV is the first 12 bytes of the ciphertext; never reused.
        assertFalse(ctA.copyOfRange(0, 12).contentEquals(ctB.copyOfRange(0, 12)), "per-store IV must be random")
    }

    // ---- No plaintext leak ----

    @Test
    fun recordContainsNoPlaintextSeed() = runTest {
        val store = CiphertextStore.inMemory()
        SeedVault(store).store(seed.copyOf(), password.toCharArray())
        val blob = store.read()!!
        // The contiguous seed must not appear anywhere in the persisted record.
        val present = (0..blob.size - seed.size).any { i ->
            blob.copyOfRange(i, i + seed.size).contentEquals(seed)
        }
        assertFalse(present, "plaintext seed must never appear in the stored record")
    }
}
