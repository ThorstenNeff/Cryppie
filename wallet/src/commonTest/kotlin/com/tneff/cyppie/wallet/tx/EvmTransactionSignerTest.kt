package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.evm.abi.Erc20Abi
import com.tneff.cyppie.evm.rlp.Rlp
import com.tneff.cyppie.evm.rlp.RlpItem
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvmTransactionSignerTest {

    private val keyManager =
        EvmKeyManager(SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed()))
    private val signer = EvmTransactionSigner(keyManager)

    // Hardhat account #0.
    private val account0 = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")

    private fun sampleTx(chainId: Long = 1L) = Eip1559Transaction(
        chainId = chainId,
        nonce = Quantity.of(0),
        maxPriorityFeePerGas = Quantity.of(1_000_000_000L),   // 1 gwei
        maxFeePerGas = Quantity.of(20_000_000_000L),          // 20 gwei
        gasLimit = Quantity.of(21_000L),
        to = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
        value = Quantity.of(1_000_000_000_000_000_000L),      // 1 ETH
        data = ByteArray(0),
    )

    @Test
    fun signedTxRecoversToSigner() {
        // Cryptographically validates the whole pipeline: hash → sign → recId/yParity.
        val tx = sampleTx()
        val signed = signer.sign(tx, accountIndex = 0)
        assertEquals(account0, signer.recoverSigner(tx, signed))
        assertTrue(signed.yParity == 0 || signed.yParity == 1)
        assertEquals(32, signed.transactionHash.size)
    }

    @Test
    fun rawTxIsType2AndDecodesToTwelveFields() {
        val signed = signer.sign(sampleTx(chainId = 8453L), accountIndex = 0) // Base
        assertEquals(0x02, signed.rawTransaction[0].toInt() and 0xFF)
        assertTrue(signed.rawTransactionHex.startsWith("0x02"))

        val payload = signed.rawTransaction.copyOfRange(1, signed.rawTransaction.size)
        val decoded = Rlp.decode(payload) as RlpItem.Lst
        assertEquals(12, decoded.items.size) // 9 unsigned + yParity + r + s
        val chainId = Quantity.ofBytes((decoded.items[0] as RlpItem.Str).bytes)
        assertEquals(Quantity.of(8453L), chainId)
    }

    @Test
    fun deterministicSigning() {
        val tx = sampleTx()
        val a = signer.sign(tx, 0)
        val b = signer.sign(tx, 0)
        assertEquals(a.rawTransactionHex, b.rawTransactionHex) // RFC-6979 deterministic
    }

    @Test
    fun erc20TransferTxRecoversToSigner() {
        val tx = sampleTx().copy(
            to = EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"), // token contract
            value = Quantity.ZERO,
            data = Erc20Abi.transfer(
                EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
                Quantity.of(1_000_000L),
            ),
        )
        val signed = signer.sign(tx, 0)
        assertEquals(account0, signer.recoverSigner(tx, signed))
    }
}
