package com.tneff.cyppie.evm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KAN-57 — authoritative **EIP-55** known-answer vectors for [EvmAddress] (ADR-0014 audit gate).
 *
 * The full set of eight example addresses from the EIP-55 specification (Test Cases section,
 * <https://eips.ethereum.org/EIPS/eip-55>, verified verbatim 2026-06-18): two all-caps, two
 * all-lower, four mixed-case. Independent of the Dev self-tests; negative cases assert the sealed
 * [EvmException.InvalidAddress]. No secrets involved (public addresses only).
 */
class Eip55VectorsTest {

    private val eip55 = listOf(
        "0x52908400098527886E0F7030069857D2E4169EE7", // all caps
        "0x8617E340B3D01FA5F11F306F4090FD50E238070D", // all caps
        "0xde709f2102306220921060314715629080e2fb77", // all lower
        "0x27b1fdb04752bbc536007a920d24acb045561c26", // all lower
        "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed", // mixed
        "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359", // mixed
        "0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB", // mixed
        "0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb", // mixed
    )

    @Test
    fun allSpecVectorsChecksumRoundTrip() {
        for (a in eip55) {
            val body = a.removePrefix("0x")
            // Canonical input round-trips, and the checksum is re-derived from case-stripped input.
            assertEquals(a, EvmAddress.parse(a).value, "round-trip $a")
            assertEquals(a, EvmAddress.parse("0x" + body.lowercase()).value, "checksum from lowercase $a")
            assertEquals(a, EvmAddress.parse("0x" + body.uppercase()).value, "checksum from uppercase $a")
            assertTrue(EvmAddress.isValid(a), "isValid $a")
            // fromBytes(parsed.bytes) reproduces the same checksummed value.
            assertEquals(a, EvmAddress.fromBytes(EvmAddress.parse(a).bytes).value)
        }
    }

    @Test
    fun rejectsMisCasedChecksum() {
        // Flip exactly one nibble's case in a mixed-case vector → must fail the EIP-55 check.
        val bad = "0x5AAeb6053F3E94C9b9A09f33669435E7Ef1BeAed" // '5a…' → '5A…'
        assertFalse(EvmAddress.isValid(bad))
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.parse(bad) }
    }

    @Test
    fun rejectsMalformedAddresses() {
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAe") } // 39 hex
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAedd") } // 41 hex
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.parse("5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed") } // no 0x
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.parse("0xZZAeb6053F3E94C9b9A09f33669435E7Ef1BeAed") } // non-hex
        assertFailsWith<EvmException.InvalidAddress> { EvmAddress.fromBytes(ByteArray(19)) } // wrong byte length
    }
}
