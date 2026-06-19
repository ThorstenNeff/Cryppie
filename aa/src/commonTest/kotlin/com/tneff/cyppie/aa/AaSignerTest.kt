package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.RecoverableSignature
import com.tneff.cyppie.wallet.SeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SeedSource that records zeroization, so we can assert the minimal seed window (M1), mirroring :send. */
private class CloseableSeed(seed: ByteArray) : SeedSource, AutoCloseable {
    private val delegate = SeedSource.ofSeed(seed)
    var closed = false
    override fun <R> withSeed(block: (ByteArray) -> R): R = delegate.withSeed(block)
    override fun close() { closed = true }
}

class AaSignerTest {
    private val seed = Mnemonic.of("test test test test test test test test test test test junk").toSeed()
    private val account0 = EvmKeyManager(SeedSource.ofSeed(seed)).deriveAccount(0)
    private val digest = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun signsDigest_zeroizes_andRecoversToAccount0() {
        val source = CloseableSeed(seed)
        val sigHex = AaSigner(accountIndex = 0).signDigest(digest, source)

        // 65-byte 0x signature (r‖s‖v).
        assertTrue(sigHex.startsWith("0x"))
        assertEquals(130, sigHex.removePrefix("0x").length) // 65 bytes

        // Seed zeroized immediately after signing (M1).
        assertTrue(source.closed, "seed must be zeroized right after signing")

        // v = recId + 27 ∈ {27,28}; the signature recovers to the on-device account (= SIWE identity).
        val raw = Hex.decodeOrNull(sigHex.removePrefix("0x"))!!
        val v = raw[64].toInt() and 0xff
        assertTrue(v == 27 || v == 28)
        val recovered = RecoverableSignature(raw.copyOfRange(0, 32), raw.copyOfRange(32, 64), v - 27)
            .recoverAddress(digest)
        assertEquals(account0.address, recovered)
    }

    @Test
    fun badDigestSize_throws_andStillZeroizes() {
        val source = CloseableSeed(seed)
        assertFailsWith<IllegalArgumentException> { AaSigner().signDigest(ByteArray(31), source) }
        assertTrue(source.closed, "seed zeroized even on bad input (fail-closed)")
    }

    @Test
    fun hexOverload_decodesAndSigns() {
        val source = CloseableSeed(seed)
        val sigHex = AaSigner().signDigest("0x" + Hex.encode(digest), source)
        assertEquals(130, sigHex.removePrefix("0x").length)
        assertTrue(source.closed)
    }
}
