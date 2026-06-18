package com.tneff.cyppie.feature.onboarding

/**
 * Onboarding's seam to the L1 `:wallet` BIP-39 API (ONB-5/6). `:wallet` has no js/wasm targets
 * (secp256k1, ADR-0008/0016), while `:feature:onboarding` does — so onboarding cannot depend on it
 * in `commonMain`. This `expect`/`actual` keeps `commonMain` web-compilable: android/ios/jvm delegate
 * to `com.tneff.cyppie.wallet.Mnemonic`; js/wasm are **unsupported** ([supported] = false), matching
 * "Web is read-only, no key ops". The seed screens are reachable only on non-web targets.
 *
 * Only primitive `List<String>`/`Boolean` cross the seam — never a `Mnemonic` (a `:wallet` type).
 */
expect object MnemonicSupport {
    /** Whether BIP-39 key ops are available on this target (false on web). */
    val supported: Boolean

    /** Validates a full BIP-39 phrase (words + checksum). */
    fun isValid(words: List<String>): Boolean

    /** Whether [word] is in the BIP-39 English wordlist (per-word validation). */
    fun isWord(word: String): Boolean

    /** Up to [limit] wordlist entries starting with [prefix] (autocomplete). */
    fun suggestions(prefix: String, limit: Int = 5): List<String>

    /** Generates a fresh phrase (CSPRNG inside `:wallet`); 12 or 24 words. */
    fun generate(wordCount: Int): List<String>
}
