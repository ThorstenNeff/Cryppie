package com.tneff.cyppie.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KAN-26 AK — BIP-39 support used by ONB-5 (per-word validity, full-phrase checksum, autocomplete).
 * Pure/multiplatform (`commonTest`). Drives the ImportSeed gating: a word is accepted per-cell when
 * [MnemonicSupport.isWord]; the **checksum** ([MnemonicSupport.isValid]) is only enforced on the
 * import *attempt* (On-Attempt banner). Only public test mnemonics — no real seeds (§4.3).
 */
class MnemonicSupportTest {

    private val abandonAbout = // valid 12-word (checksum OK)
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
    private val allAbandon = List(12) { "abandon" } // all valid WORDS, but checksum FAILS
    private val hardhat = "test test test test test test test test test test test junk".split(" ")

    @Test
    fun isWordAcceptsBip39WordsAndRejectsOthers() {
        for (w in listOf("abandon", "about", "zoo", "test", "junk")) assertTrue(MnemonicSupport.isWord(w), w)
        for (w in listOf("zzzz", "notaword", "", "xyzzy", "abandonx")) assertFalse(MnemonicSupport.isWord(w), w)
    }

    @Test
    fun isValidChecksum() {
        assertTrue(MnemonicSupport.isValid(abandonAbout), "abandon…about is a valid checksummed phrase")
        assertTrue(MnemonicSupport.isValid(hardhat), "hardhat test…junk is valid")
        // All valid words but wrong checksum → invalid (this is what the On-Attempt banner catches).
        assertFalse(MnemonicSupport.isValid(allAbandon), "12x abandon has a bad checksum")
        // Wrong length.
        assertFalse(MnemonicSupport.isValid(List(11) { "abandon" }))
        // Contains a non-word.
        assertFalse(MnemonicSupport.isValid(abandonAbout.dropLast(1) + "zzzz"))
    }

    @Test
    fun suggestionsCompletePrefixes() {
        val aban = MnemonicSupport.suggestions("aban", limit = 5)
        assertTrue(aban.contains("abandon"), "prefix 'aban' should suggest 'abandon'")
        assertTrue(aban.all { it.startsWith("aban") })
        assertTrue(MnemonicSupport.suggestions("zo", limit = 5).contains("zoo"))
        // No completion for a non-prefix.
        assertEquals(emptyList(), MnemonicSupport.suggestions("zzzz", limit = 5))
    }

    @Test
    fun allAbandonWordsAreIndividuallyValid_soImportEnablesButChecksumFails() {
        // Directly mirrors the ImportSeed gate: every cell valid ⇒ import enabled; isValid ⇒ false.
        assertTrue(allAbandon.all { MnemonicSupport.isWord(it) })
        assertFalse(MnemonicSupport.isValid(allAbandon))
    }
}
