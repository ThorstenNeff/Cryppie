package com.tneff.cyppie.wallet

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MnemonicTest {

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
