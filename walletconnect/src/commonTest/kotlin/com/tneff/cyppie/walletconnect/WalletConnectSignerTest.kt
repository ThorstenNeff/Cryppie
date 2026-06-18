package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.RecoverableSignature
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.tx.Eip1559Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletConnectSignerTest {

    private val keyManager =
        EvmKeyManager(SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed()))
    private val signer = WalletConnectSigner(keyManager)
    private val account0 = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")

    @Test
    fun personalSignIsEip191AndRecoversToSigner() {
        val message = "Hello Cyppie".encodeToByteArray()
        val sig = signer.personalSign(message, accountIndex = 0)
        assertEquals(65, sig.size)
        val v = sig[64].toInt() and 0xFF
        assertTrue(v == 27 || v == 28)

        // Recompute the EIP-191 digest and recover the signer.
        val prefix = "Ethereum Signed Message:\n${message.size}".encodeToByteArray()
        val digest = Keccak.keccak256(prefix + message)
        val recovered = RecoverableSignature(
            r = sig.copyOfRange(0, 32),
            s = sig.copyOfRange(32, 64),
            recId = v - 27,
        ).recoverAddress(digest)
        assertEquals(account0, recovered)
    }

    @Test
    fun personalSignIsDeterministic() {
        val m = "nonce-42".encodeToByteArray()
        assertTrue(signer.personalSign(m, 0).contentEquals(signer.personalSign(m, 0)))
    }

    @Test
    fun signTransactionRecoversToSigner() {
        val tx = Eip1559Transaction(
            chainId = 1L,
            nonce = Quantity.of(0),
            maxPriorityFeePerGas = Quantity.of(1_000_000_000L),
            maxFeePerGas = Quantity.of(20_000_000_000L),
            gasLimit = Quantity.of(21_000L),
            to = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
            value = Quantity.of(1_000_000_000_000_000_000L),
        )
        val signed = signer.signTransaction(tx, accountIndex = 0)
        assertTrue(signed.rawTransactionHex.startsWith("0x02"))
        assertEquals(32, signed.transactionHash.size)
    }
}
