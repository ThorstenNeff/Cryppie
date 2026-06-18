package com.tneff.cyppie.wallet

/**
 * A validated BIP-39 mnemonic (PO guardrail #1). Construction enforces the BIP-39 checksum, so a
 * `Mnemonic` instance is always a valid phrase — no raw, unchecked string is ever passed through
 * the layer. The 64-byte seed is produced on demand via [toSeed]; the seed itself is not cached.
 */
class Mnemonic private constructor(words: List<String>) {

    /** The phrase words (lowercase, space-separable). Sensitive — display/back-up use only. */
    val words: List<String> = words.toList()

    /** PBKDF2-HMAC-SHA512 BIP-39 seed (64 bytes). [passphrase] is the optional BIP-39 "25th word". */
    fun toSeed(passphrase: String = ""): ByteArray =
        EvmCrypto.mnemonicToSeed(words, passphrase)

    /** Never render the phrase via toString — avoids accidental logging of the secret. */
    override fun toString(): String = "Mnemonic(${words.size} words)"

    companion object {
        /** Validates and wraps a list of words; throws [WalletKeyException.InvalidMnemonic] if invalid. */
        fun of(words: List<String>): Mnemonic {
            val normalized = words.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (!EvmCrypto.isValidMnemonic(normalized)) {
                throw WalletKeyException.InvalidMnemonic("Invalid BIP-39 mnemonic")
            }
            return Mnemonic(normalized)
        }

        /** Validates and wraps a whitespace-separated phrase. */
        fun of(sentence: String): Mnemonic = of(sentence.trim().split(Regex("\\s+")))

        /**
         * Builds a phrase from raw [entropy] (16/20/24/28/32 bytes → 12/15/18/21/24 words).
         * Caller must supply CSPRNG entropy (e.g. cryptography-kotlin, ADR-0008).
         */
        fun fromEntropy(entropy: ByteArray): Mnemonic = Mnemonic(EvmCrypto.entropyToMnemonic(entropy))

        /** True iff [words] are a valid BIP-39 phrase. */
        fun isValid(words: List<String>): Boolean =
            EvmCrypto.isValidMnemonic(words.map { it.trim().lowercase() }.filter { it.isNotEmpty() })
    }
}
