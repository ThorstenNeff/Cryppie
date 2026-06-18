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

        /**
         * Generates a fresh BIP-39 mnemonic of [wordCount] words (12 or 24) from CSPRNG entropy
         * (KAN-72). Entropy is drawn in the crypto layer ([EvmCrypto.secureRandomBytes],
         * CryptographyRandom) — never in the UI. 12 words = 128 bits, 24 words = 256 bits.
         */
        fun generate(wordCount: Int = 12): Mnemonic {
            val entropyBytes = when (wordCount) {
                12 -> 16
                24 -> 32
                else -> throw IllegalArgumentException("wordCount must be 12 or 24, was $wordCount")
            }
            return fromEntropy(EvmCrypto.secureRandomBytes(entropyBytes))
        }

        /** True iff [words] are a valid BIP-39 phrase. */
        fun isValid(words: List<String>): Boolean =
            EvmCrypto.isValidMnemonic(words.map { it.trim().lowercase() }.filter { it.isNotEmpty() })

        // ---- BIP-39 wordlist (single source for ONB-5 per-word validation + autocomplete) ----

        /** The 2048-word BIP-39 English wordlist (alphabetical), exposed so the UI never duplicates it. */
        val wordlist: List<String> get() = englishWordlist
        private val englishWordlist: List<String> by lazy { EvmCrypto.bip39EnglishWordlist() }
        private val wordSet: Set<String> by lazy { englishWordlist.toHashSet() }

        /** True iff [word] is in the BIP-39 English wordlist (trimmed, case-insensitive). */
        fun isWord(word: String): Boolean = word.trim().lowercase() in wordSet

        /**
         * Up to [limit] wordlist entries starting with [prefix] (trimmed, case-insensitive),
         * in alphabetical order — for seed-entry autocomplete. Empty prefix or limit ≤ 0 → empty.
         */
        fun suggestions(prefix: String, limit: Int = 5): List<String> {
            val p = prefix.trim().lowercase()
            if (p.isEmpty() || limit <= 0) return emptyList()
            return englishWordlist.asSequence().filter { it.startsWith(p) }.take(limit).toList()
        }
    }
}
