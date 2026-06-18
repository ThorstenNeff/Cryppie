package com.tneff.cyppie.storage

/**
 * Biometric/hardware convenience layer (ADR-0009): a platform-protected store that releases a small
 * secret (the app password or KEK) after a biometric/device-credential check. This is the **ONB-9
 * seam**; the password remains the cryptographic gate, so the wallet is fully usable without it.
 *
 * Real implementations (Android Keystore `setUserAuthenticationRequired`, iOS Keychain +
 * Secure Enclave / `LAContext`) land as platform `actual`s; [NoopSecureKeyStore] is the
 * password-primary default (Desktop, and until the hardware actuals are wired).
 */
interface SecureKeyStore {

    /** True if entries are backed by hardware (Keystore/Secure Enclave) rather than password-only. */
    val isHardwareBacked: Boolean

    /** Stores [secret] under [alias], gated by biometrics/device credential on retrieval. */
    suspend fun protect(alias: String, secret: ByteArray)

    /** Releases the secret for [alias] (may trigger a biometric prompt), or null if absent. */
    suspend fun retrieve(alias: String): ByteArray?

    /** Removes the entry for [alias]. */
    suspend fun clear(alias: String)
}

/**
 * Password-primary fallback: no hardware protection, stores nothing. Biometric unlock is simply
 * unavailable, so callers fall back to the password gate. Safe default for Desktop and tests.
 */
object NoopSecureKeyStore : SecureKeyStore {
    override val isHardwareBacked: Boolean = false
    override suspend fun protect(alias: String, secret: ByteArray) = Unit
    override suspend fun retrieve(alias: String): ByteArray? = null
    override suspend fun clear(alias: String) = Unit
}

/**
 * The single canonical alias for the wallet's biometric-unlock secret. Shared between [SeedVault]
 * (which clears it on `store`/`clear`) and the platform key stores (Android enroll, iOS Keychain) so
 * a wallet reset/overwrite **always** wipes the biometric credential — never leaving a now-stale
 * password recoverable behind a wiped seed (KAN-82). Do not introduce a second alias.
 */
internal const val SEED_UNLOCK_ALIAS: String = "cyppie.seed.unlock"
