package com.tneff.cyppie.wallet

/**
 * Internal crypto SPI — the only seam that touches the non-web crypto stack
 * (ACINQ bitcoin-kmp / secp256k1-kmp; ADR-0008). keccak lives web-safe in `:evm`.
 *
 * Declared as an `expect object` so the heavy native dependencies live exclusively in the
 * platform source sets (they ship no Android-AAR variant), while `commonMain` stays
 * dependency-free and compilable for every target. This also satisfies ADR-0014's mandate to
 * keep the security-critical primitives isolated behind a thin internal API.
 *
 * All functions are pure and synchronous (PO guardrail #6). Inputs/outputs are raw bytes;
 * no key material is ever logged or stringified.
 */
internal expect object EvmCrypto {

    // ---- BIP-39 ----

    /** True iff [words] form a valid BIP-39 phrase (wordlist + length + checksum). */
    fun isValidMnemonic(words: List<String>): Boolean

    /** PBKDF2-HMAC-SHA512 BIP-39 seed (64 bytes) from [words] + optional [passphrase]. */
    fun mnemonicToSeed(words: List<String>, passphrase: String): ByteArray

    /** Encodes [entropy] (16/20/24/28/32 bytes) into a BIP-39 phrase. */
    fun entropyToMnemonic(entropy: ByteArray): List<String>

    // ---- BIP-32 / secp256k1 ----

    /** BIP-32 derive the 32-byte private key at [path] from the 64-byte BIP-39 [seed]. */
    fun derivePrivateKey(seed: ByteArray, path: String): ByteArray

    /** Uncompressed secp256k1 public key (65 bytes, `0x04 || X || Y`) for a 32-byte [privateKey]. */
    fun publicKey(privateKey: ByteArray): ByteArray

    /** Low-S recoverable ECDSA signature of the 32-byte [hash] under [privateKey]. */
    fun sign(privateKey: ByteArray, hash: ByteArray): RecoverableSignature

    /** Recovers the uncompressed (65-byte) public key that produced [signature] over [hash]. */
    fun recoverPublicKey(hash: ByteArray, signature: RecoverableSignature): ByteArray

    // ---- RNG ----

    /** [size] cryptographically-secure random bytes (CryptographyRandom, ADR-0008) for BIP-39 entropy. */
    fun secureRandomBytes(size: Int): ByteArray
}
