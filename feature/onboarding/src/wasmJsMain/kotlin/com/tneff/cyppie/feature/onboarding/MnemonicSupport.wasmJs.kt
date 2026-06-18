package com.tneff.cyppie.feature.onboarding

// Web is read-only — no BIP-39 key ops (:wallet has no js/wasm; ADR-0008/0016). Seed screens are
// not reachable on web; these return "unavailable" rather than crashing the (web-compilable) module.
actual object MnemonicSupport {
    actual val supported: Boolean = false
    actual fun isValid(words: List<String>): Boolean = false
    actual fun isWord(word: String): Boolean = false
    actual fun suggestions(prefix: String, limit: Int): List<String> = emptyList()
    actual fun generate(wordCount: Int): List<String> =
        throw IllegalStateException("Wallet key operations are not available on web")
}
