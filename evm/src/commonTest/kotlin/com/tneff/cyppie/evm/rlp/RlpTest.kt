package com.tneff.cyppie.evm.rlp

import com.tneff.cyppie.evm.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Canonical RLP examples from the Ethereum RLP spec / Yellow Paper Appendix B. */
class RlpTest {

    private fun enc(item: RlpItem) = Hex.encode(Rlp.encode(item))

    @Test
    fun encodesSpecVectors() {
        assertEquals("83646f67", enc(Rlp.string("dog".encodeToByteArray())))
        assertEquals("80", enc(Rlp.string(ByteArray(0))))            // empty string
        assertEquals("c0", enc(Rlp.list()))                          // empty list
        assertEquals("00", enc(Rlp.string(byteArrayOf(0x00))))       // the single byte 0x00
        assertEquals("0f", enc(Rlp.string(byteArrayOf(0x0f))))       // single byte < 0x80
        assertEquals("820400", enc(Rlp.string(byteArrayOf(0x04, 0x00)))) // the bytes 0x0400
        assertEquals(
            "c88363617483646f67",
            enc(Rlp.list(Rlp.string("cat".encodeToByteArray()), Rlp.string("dog".encodeToByteArray()))),
        )
    }

    @Test
    fun encodesLongStringWithLengthPrefix() {
        // 56 bytes → 0xb8, 0x38 (=56), payload
        val text = "Lorem ipsum dolor sit amet, consectetur adipisicing elit"
        assertEquals(56, text.length)
        val encoded = Rlp.encode(Rlp.string(text.encodeToByteArray()))
        assertEquals(0xb8, encoded[0].toInt() and 0xFF)
        assertEquals(0x38, encoded[1].toInt() and 0xFF)
    }

    @Test
    fun roundTripsNestedStructure() {
        val original = Rlp.list(
            Rlp.string(byteArrayOf(0x01)),
            Rlp.list(Rlp.string("cat".encodeToByteArray()), Rlp.string(ByteArray(0))),
            Rlp.string(Hex.decodeOrNull("deadbeef".repeat(20))!!), // 80-byte string → long form
        )
        val decoded = Rlp.decode(Rlp.encode(original))
        assertEquals(original, decoded)
    }
}
