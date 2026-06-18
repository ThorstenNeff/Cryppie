package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.evm.rlp.Rlp
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.RecoverableSignature
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.WalletKeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * KAN-59 — EIP-1559 (Type-2) **encoding/signature audit KATs** (ADR-0014 gate).
 *
 * No external signing oracle is available, so byte-for-byte correctness is anchored two ways that
 * are independent of the signer's *private* encoder:
 *  1. The signing hash and the broadcast raw are re-assembled **test-side** strictly per the
 *     EIP-2718/EIP-1559 spec (`0x02 || rlp([...])`) using the public `Rlp` + `Keccak` primitives —
 *     themselves KAT'd against the Yellow-Paper RLP vectors ([com.tneff.cyppie.evm.rlp.RlpEdgeVectorsTest])
 *     and EIP-55/keccak — and asserted equal to the signer's output. A wrong field order, missing
 *     access list, wrong type byte or r/s/yParity ordering would diverge and fail.
 *  2. The produced signature **recovers to the expected signer** (cryptographic validity).
 *
 * Deterministic (RFC-6979) so the raw is stable. Only the public Hardhat test mnemonic — no real keys.
 * (A go-ethereum byte-for-byte signed-tx vector can be grafted on once a verified one is available.)
 */
class Eip1559VectorsTest {

    private val keyManager = EvmKeyManager(
        SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed()),
    )
    private val signer = EvmTransactionSigner(keyManager)
    private val account0 = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")

    private val tx = Eip1559Transaction(
        chainId = 1L,
        nonce = Quantity.of(9L),
        maxPriorityFeePerGas = Quantity.of(1_500_000_000L), // 1.5 gwei
        maxFeePerGas = Quantity.of(30_000_000_000L),        // 30 gwei
        gasLimit = Quantity.of(21_000L),
        to = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
        value = Quantity.of(1_000_000_000_000_000_000L),    // 1 ETH
        data = ByteArray(0),
    )

    private fun unsignedFields() = listOf(
        Rlp.string(Quantity.of(tx.chainId).toMinimalBytes()),
        Rlp.string(tx.nonce.toMinimalBytes()),
        Rlp.string(tx.maxPriorityFeePerGas.toMinimalBytes()),
        Rlp.string(tx.maxFeePerGas.toMinimalBytes()),
        Rlp.string(tx.gasLimit.toMinimalBytes()),
        Rlp.string(tx.to!!.bytes),
        Rlp.string(tx.value.toMinimalBytes()),
        Rlp.string(tx.data),
        Rlp.list(), // empty access list
    )

    @Test
    fun signingHashMatchesIndependentSpecEncoding() {
        val expected = Keccak.keccak256(byteArrayOf(0x02) + Rlp.encode(RlpItemList(unsignedFields())))
        assertEquals(Hex.encode(expected), Hex.encode(signer.signingHash(tx)))
    }

    @Test
    fun rawTransactionMatchesIndependentSpecEncoding() {
        val signed = signer.sign(tx, accountIndex = 0)
        val signedFields = unsignedFields() + listOf(
            Rlp.string(Quantity.of(signed.yParity.toLong()).toMinimalBytes()),
            Rlp.string(Quantity.ofBytes(signed.signature.r).toMinimalBytes()),
            Rlp.string(Quantity.ofBytes(signed.signature.s).toMinimalBytes()),
        )
        val expectedRaw = byteArrayOf(0x02) + Rlp.encode(RlpItemList(signedFields))
        assertEquals(Hex.encode(expectedRaw), Hex.encode(signed.rawTransaction))
        assertEquals("0x" + Hex.encode(expectedRaw), signed.rawTransactionHex)
        assertEquals(0x02, signed.rawTransaction[0].toInt() and 0xFF)
    }

    @Test
    fun signatureRecoversToSigner_andYParityIsBit() {
        val signed = signer.sign(tx, 0)
        assertEquals(account0, signer.recoverSigner(tx, signed))
        assertTrue(signed.yParity == 0 || signed.yParity == 1, "typed-tx v is a parity bit")
    }

    @Test
    fun deterministicRawHex() {
        assertEquals(signer.sign(tx, 0).rawTransactionHex, signer.sign(tx, 0).rawTransactionHex)
    }

    @Test
    fun recoversFromReconstructedExternalSignature() {
        // Rebuild a SignedTransaction from the signature values alone → recovery still yields the signer.
        val signed = signer.sign(tx, 0)
        val rebuilt = SignedTransaction(
            rawTransaction = signed.rawTransaction,
            transactionHash = signed.transactionHash,
            signature = RecoverableSignature(signed.signature.r, signed.signature.s, signed.signature.recId),
            yParity = signed.yParity,
        )
        assertEquals(account0, signer.recoverSigner(tx, rebuilt))
    }

    @Test
    fun rejectsWrongSigningInput() {
        assertFailsWith<WalletKeyException.InvalidSigningInput> { keyManager.sign(0, ByteArray(31)) }
    }
}

/** Local helper to build an `RlpItem.Lst` from a field list (avoids importing the class name twice). */
private fun RlpItemList(fields: List<com.tneff.cyppie.evm.rlp.RlpItem>): com.tneff.cyppie.evm.rlp.RlpItem.Lst =
    com.tneff.cyppie.evm.rlp.RlpItem.Lst(fields)
