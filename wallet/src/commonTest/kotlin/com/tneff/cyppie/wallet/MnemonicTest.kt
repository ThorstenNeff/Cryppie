package com.tneff.cyppie.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MnemonicTest {

    @Test
    fun generatesValid12And24WordMnemonics() {
        val m12 = Mnemonic.generate(12)
        assertEquals(12, m12.words.size)
        assertTrue(Mnemonic.isValid(m12.words))
        // round-trips through the public validators (PO AK)
        assertEquals(m12.words, Mnemonic.of(m12.words).words)

        val m24 = Mnemonic.generate(24)
        assertEquals(24, m24.words.size)
        assertTrue(Mnemonic.isValid(m24.words))
    }

    @Test
    fun generatesDifferentMnemonicEachCall() {
        // CSPRNG entropy → distinct phrases (collision probability negligible)
        assertNotEquals(Mnemonic.generate(12).words, Mnemonic.generate(12).words)
        assertNotEquals(Mnemonic.generate(24).words, Mnemonic.generate(24).words)
    }

    @Test
    fun rejectsUnsupportedWordCount() {
        assertFailsWith<IllegalArgumentException> { Mnemonic.generate(13) }
        assertFailsWith<IllegalArgumentException> { Mnemonic.generate(18) }
    }

    @Test
    fun wordlistIsTheCanonical2048() {
        assertEquals(2048, Mnemonic.wordlist.size)
        assertEquals("abandon", Mnemonic.wordlist.first())
        assertEquals("zoo", Mnemonic.wordlist.last())
    }

    @Test
    fun isWordValidatesAgainstWordlist() {
        assertTrue(Mnemonic.isWord("abandon"))
        assertTrue(Mnemonic.isWord("  Zoo "))   // trimmed + case-insensitive
        assertFalse(Mnemonic.isWord("notaword"))
        assertFalse(Mnemonic.isWord(""))
    }

    @Test
    fun suggestionsReturnAlphabeticalPrefixMatches() {
        assertEquals(listOf("abandon"), Mnemonic.suggestions("aban"))         // only word with this prefix
        assertEquals(listOf("abandon", "ability", "able"), Mnemonic.suggestions("ab", limit = 3))
        assertTrue(Mnemonic.suggestions("zz").isEmpty())                       // no matches
        assertTrue(Mnemonic.suggestions("ab", limit = 0).isEmpty())           // limit guard
        assertTrue(Mnemonic.suggestions("").isEmpty())                         // empty prefix
        assertTrue(Mnemonic.suggestions("ZO").all { it.startsWith("zo") })     // case-insensitive
    }

    @Test
    fun acceptsValidPhrase() {
        assertTrue(Mnemonic.isValid("test test test test test test test test test test test junk".split(" ")))
    }

    @Test
    fun rejectsInvalidChecksum() {
        val bad = List(12) { "abandon" } // 11×abandon + "about" is valid; 12×abandon fails the checksum
        assertFalse(Mnemonic.isValid(bad))
        assertFailsWith<WalletKeyException.InvalidMnemonic> { Mnemonic.of(bad) }
    }

    @Test
    fun rejectsUnknownWord() {
        assertFailsWith<WalletKeyException.InvalidMnemonic> {
            Mnemonic.of("test test test test test test test test test test test notaword")
        }
    }
}
