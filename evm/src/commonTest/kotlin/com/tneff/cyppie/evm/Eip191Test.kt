package com.tneff.cyppie.evm

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * KAN-141 — known-answer + construction vectors for the shared [Eip191] personal-sign digest.
 *
 * The two hex KATs are **externally anchored**: `:walletconnect`'s `WcSignerExternalVectorsTest` signs
 * exactly these digests and asserts the resulting 65-byte signature against **eth-account 0.13.7** literals
 * under Anvil account #0 — so a wrong prefix / length encoding would diverge there. The construction tests
 * additionally pin the byte-level prefix independently of the implementation (defeating the masked-`0x19`
 * trap: the expectation is rebuilt from primitives, not by calling [Eip191]).
 */
class Eip191Test {

    /** Independent re-build of `keccak256(0x19 ‖ "Ethereum Signed Message:\n" ‖ len ‖ message)`. */
    private fun reconstruct(message: ByteArray): ByteArray =
        Keccak.keccak256(byteArrayOf(0x19) + "Ethereum Signed Message:\n${message.size}".encodeToByteArray() + message)

    @Test
    fun knownAnswer_helloCyppie() {
        // Cross-validated by WcSignerExternalVectorsTest.personalSign_helloCyppie_matchesEthAccount.
        assertEquals(
            "e99ad39a8be59a0df3a63711f47f39b431c06690fac3622823ac7e53816a1c25",
            Hex.encode(Eip191.personalSignDigest("Hello Cyppie".encodeToByteArray())),
        )
    }

    @Test
    fun knownAnswer_nonce42() {
        // Cross-validated by WcSignerExternalVectorsTest.personalSign_nonce42_zeroPaddedR_matchesEthAccount.
        assertEquals(
            "e53288a3682444a8165c12c449f95f79e3e0c645f9a212e29cf1e8f9ebc27c38",
            Hex.encode(Eip191.personalSignDigest("nonce-42".encodeToByteArray())),
        )
    }

    @Test
    fun matchesSpecConstruction() {
        val message = "Hello Cyppie".encodeToByteArray()
        assertContentEquals(reconstruct(message), Eip191.personalSignDigest(message))
    }

    @Test
    fun emptyMessageUsesZeroLength() {
        // prefix ends in "\n0" — guards the empty-message edge.
        assertContentEquals(reconstruct(ByteArray(0)), Eip191.personalSignDigest(ByteArray(0)))
    }

    @Test
    fun lengthIsByteCountNotCharCount() {
        // "é" is 1 char but 2 UTF-8 bytes → the prefix length must be 2, not 1.
        val message = "é".encodeToByteArray()
        assertEquals(2, message.size)
        assertContentEquals(reconstruct(message), Eip191.personalSignDigest(message))
    }

    @Test
    fun multiDigitLengthsAreAsciiDecimal() {
        // Two-digit (12) and three-digit (256) byte lengths exercise the decimal length encoding.
        val twoDigit = ByteArray(12) { 'a'.code.toByte() }
        val threeDigit = ByteArray(256) { 'b'.code.toByte() }
        assertContentEquals(reconstruct(twoDigit), Eip191.personalSignDigest(twoDigit))
        assertContentEquals(reconstruct(threeDigit), Eip191.personalSignDigest(threeDigit))
    }
}
