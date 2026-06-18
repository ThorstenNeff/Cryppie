package com.tneff.cyppie.evm.rlp

import com.tneff.cyppie.evm.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.tneff.cyppie.evm.EvmException

/**
 * KAN-59 — authoritative RLP **edge-case** known-answer vectors (Yellow Paper Appendix B /
 * ethereumjs `rlptest`), complementing the basic spec vectors in [RlpTest]. Covers the length-prefix
 * boundaries (55/56 bytes), single bytes around 0x80, the nested "set-theoretic three", and long
 * lists, plus decode round-trips and malformed-input rejection (`EvmException.InvalidRlp`).
 */
class RlpEdgeVectorsTest {

    private fun enc(item: RlpItem) = Hex.encode(Rlp.encode(item))
    private fun str(hex: String) = Rlp.string(Hex.decodeOrNull(hex)!!)

    @Test
    fun singleByteBoundaries() {
        assertEquals("00", enc(Rlp.string(byteArrayOf(0x00))))          // 0x00 → itself
        assertEquals("7f", enc(Rlp.string(byteArrayOf(0x7f))))          // 0x7f → itself (< 0x80)
        assertEquals("8180", enc(Rlp.string(byteArrayOf(0x80.toByte())))) // 0x80 → 0x81,0x80 (>= 0x80)
    }

    @Test
    fun shortToLongStringLengthPrefixBoundary() {
        // 55 bytes → short form 0x80+55 = 0xb7; 56 bytes → long form 0xb8,0x38.
        val s55 = ByteArray(55) { 0x61 } // 'a' * 55
        val e55 = Rlp.encode(Rlp.string(s55))
        assertEquals(0xb7, e55[0].toInt() and 0xFF)
        assertEquals(55 + 1, e55.size)

        val s56 = ByteArray(56) { 0x61 }
        val e56 = Rlp.encode(Rlp.string(s56))
        assertEquals(0xb8, e56[0].toInt() and 0xFF)
        assertEquals(0x38, e56[1].toInt() and 0xFF)
    }

    @Test
    fun setTheoreticThree() {
        // [ [], [[]], [ [], [[]] ] ] → c7 c0 c1c0 c3c0c1c0
        val three = Rlp.list(
            Rlp.list(),
            Rlp.list(Rlp.list()),
            Rlp.list(Rlp.list(), Rlp.list(Rlp.list())),
        )
        assertEquals("c7c0c1c0c3c0c1c0", enc(three))
    }

    @Test
    fun longListUsesLongLengthPrefix() {
        // A list whose payload exceeds 55 bytes → 0xf8 + length prefix.
        val item = str("deadbeef".repeat(8)) // 32-byte string
        val list = Rlp.list(item, item) // payload ≈ 2 * (1 + 32) = 66 bytes > 55
        val encoded = Rlp.encode(list)
        assertEquals(0xf8, encoded[0].toInt() and 0xFF)
    }

    @Test
    fun decodeRoundTripsAllForms() {
        val complex = Rlp.list(
            Rlp.string(ByteArray(0)),                 // empty
            Rlp.string(byteArrayOf(0x00)),            // single zero byte
            str("deadbeef".repeat(16)),               // 64-byte long string
            Rlp.list(Rlp.list(), str("c0ffee")),      // nested
        )
        assertEquals(complex, Rlp.decode(Rlp.encode(complex)))
    }

    @Test
    fun rejectsMalformedRlp() {
        // 0xb8 announces a long string but no length/payload follows.
        assertFailsWith<EvmException.InvalidRlp> { Rlp.decode(byteArrayOf(0xb8.toByte())) }
        // Trailing garbage after a complete item.
        assertFailsWith<EvmException.InvalidRlp> { Rlp.decode(byteArrayOf(0x00, 0x00)) }
    }
}
