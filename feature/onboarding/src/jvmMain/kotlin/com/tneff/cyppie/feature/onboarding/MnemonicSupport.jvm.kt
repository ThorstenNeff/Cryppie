package com.tneff.cyppie.feature.onboarding

import com.tneff.cyppie.wallet.Mnemonic

actual object MnemonicSupport {
    actual val supported: Boolean = true
    actual fun isValid(words: List<String>): Boolean = Mnemonic.isValid(words)
    actual fun isWord(word: String): Boolean = Mnemonic.isWord(word)
    actual fun suggestions(prefix: String, limit: Int): List<String> = Mnemonic.suggestions(prefix, limit)
    actual fun generate(wordCount: Int): List<String> = Mnemonic.generate(wordCount).words
}
