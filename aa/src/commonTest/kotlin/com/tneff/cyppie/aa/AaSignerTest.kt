package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.RecoverableSignature
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.WalletKeyException
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

/** Contract test for the AA orchestration over the `:wallet` UserOpSigner delegate. */
class AaSignerTest {
    private val seed = Mnemonic.of("test test test test test test test test test test test junk").toSeed()
    private val account0 = EvmKeyManager(SeedSource.ofSeed(seed)).deriveAccount(0)
    private val digest = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun signsDigest_zeroizes_andRecoversToOwner() {
        val source = CloseableSeed(seed)
        val sigHex = AaSigner().signDigest(digest, expectedOwner = account0.address, seedSource = source)

        assertTrue(sigHex.startsWith("0x"))
        assertEquals(130, sigHex.removePrefix("0x").length) // 65 bytes r‖s‖v
        assertTrue(source.closed, "seed must be zeroized right after signing")

        val raw = Hex.decodeOrNull(sigHex.removePrefix("0x"))!!
        val v = raw[64].toInt() and 0xff
        assertTrue(v == 27 || v == 28)
        val recovered = RecoverableSignature(raw.copyOfRange(0, 32), raw.copyOfRange(32, 64), v - 27)
            .recoverAddress(digest)
        assertEquals(account0.address, recovered)
    }

    @Test
    fun wrongExpectedOwner_throwsAndZeroizes() {
        val source = CloseableSeed(seed)
        val notOwner = EvmAddress.parse("0x000000000000000000000000000000000000dEaD")
        assertFailsWith<WalletKeyException.SignerMismatch> { AaSigner().signDigest(digest, notOwner, source) }
        assertTrue(source.closed, "seed zeroized even when the no-blind owner gate rejects")
    }

    @Test
    fun badDigestSize_throwsAndZeroizes() {
        val source = CloseableSeed(seed)
        assertFailsWith<WalletKeyException.InvalidSigningInput> { AaSigner().signDigest(ByteArray(31), account0.address, source) }
        assertTrue(source.closed, "seed zeroized even on bad input")
    }

    @Test
    fun hexOverload_decodesAndSigns() {
        val source = CloseableSeed(seed)
        val sigHex = AaSigner().signDigest("0x" + Hex.encode(digest), account0.address, source)
        assertEquals(130, sigHex.removePrefix("0x").length)
        assertTrue(source.closed)
    }
}
