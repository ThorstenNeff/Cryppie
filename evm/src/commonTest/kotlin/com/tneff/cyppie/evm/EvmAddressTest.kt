package com.tneff.cyppie.evm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvmAddressTest {

    // The four canonical EIP-55 examples from the spec.
    private val checksummed = listOf(
        "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
        "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
        "0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB",
        "0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb",
    )

    @Test
    fun rendersAndParsesEip55() {
        for (a in checksummed) {
            assertEquals(a, EvmAddress.parse(a).value, "round-trip checksum")
            assertEquals(a, EvmAddress.parse(a.lowercase()).value, "checksum from lowercase input")
            assertTrue(EvmAddress.isValid(a))
        }
    }

    @Test
    fun rejectsBadChecksum() {
        // Mixed-case but with one letter mis-cased → must fail the EIP-55 check.
        val bad = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1Beaed"
        assertFalse(EvmAddress.isValid(bad))
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.parse(bad) }
    }

    @Test
    fun rejectsMalformed() {
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.parse("0x1234") } // too short
        assertFailsWith<EvmException.InvalidAddress> {
            EvmAddress.parse("5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed") // missing 0x
        }
    }

    @Test
    fun equalityIsCaseInsensitiveOnBytes() {
        val a = checksummed[0]
        assertEquals(EvmAddress.parse(a), EvmAddress.parse(a.lowercase()))
    }
}
