package com.tneff.cyppie.auth

import com.tneff.cyppie.evm.Eip191
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

private class CloseableSeed(seed: ByteArray) : SeedSource, AutoCloseable {
    private val delegate = SeedSource.ofSeed(seed)
    var closed = false
    override fun <R> withSeed(block: (ByteArray) -> R): R = delegate.withSeed(block)
    override fun close() { closed = true }
}

class Eip191SiweSignerTest {
    private val seed = Mnemonic.of("test test test test test test test test test test test junk").toSeed()
    private val account0 = EvmKeyManager(SeedSource.ofSeed(seed)).deriveAccount(0)

    @Test
    fun signsSiwe_recoversToOwner_viaEip191_andZeroizes() {
        val message = SiweMessage.forSignIn(account0.address, nonce = "n0nce", issuedAt = "2026-06-19T19:00:00Z")
        val source = CloseableSeed(seed)
        val sigHex = Eip191SiweSigner().sign(message, account0.address, source)

        assertEquals(130, sigHex.removePrefix("0x").length) // 65 bytes
        assertTrue(source.closed, "seed zeroized after SIWE sign")

        val raw = Hex.decodeOrNull(sigHex.removePrefix("0x"))!!
        val digest = Eip191.personalSignDigest(message.canonical().encodeToByteArray())
        val recovered = RecoverableSignature(raw.copyOfRange(0, 32), raw.copyOfRange(32, 64), (raw[64].toInt() and 0xff) - 27)
            .recoverAddress(digest)
        assertEquals(account0.address, recovered)
    }

    @Test
    fun wrongOwner_throwsAndZeroizes() {
        val message = SiweMessage.forSignIn(account0.address, "n", "t")
        val source = CloseableSeed(seed)
        val notOwner = EvmAddress.parse("0x000000000000000000000000000000000000dEaD")
        assertFailsWith<WalletKeyException.SignerMismatch> { Eip191SiweSigner().sign(message, notOwner, source) }
        assertTrue(source.closed)
    }
}
