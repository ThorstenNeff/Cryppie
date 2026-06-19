package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-63 — **externally-referenced** EIP-1559 (type-2) signing KATs (WC-merge gate).
 *
 * The sibling [Eip1559VectorsTest] anchors correctness against the app's *own* `Rlp`/`Keccak`
 * primitives (self-consistent, but blind to a shared encoder bug). These vectors are the missing
 * **independent oracle**: byte-for-byte signed transactions produced by **eth-account 0.13.7**
 * (`Account.sign_transaction`) under the canonical Hardhat/Anvil account #0
 * (`0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266`, the public "test … junk" mnemonic — no real key).
 * A wrong RLP field order/width, type byte, low-S handling or y-parity would diverge from these
 * literals. Deterministic (RFC-6979), so the raw is stable.
 */
class Eip1559ExternalVectorsTest {

    private val keyManager = EvmKeyManager(
        SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed()),
    )
    private val signer = EvmTransactionSigner(keyManager)
    private val to = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")

    @Test
    fun nonce9_1eth_matchesEthAccountRawAndHash() {
        val tx = Eip1559Transaction(
            chainId = 1L,
            nonce = Quantity.of(9L),
            maxPriorityFeePerGas = Quantity.of(1_500_000_000L),
            maxFeePerGas = Quantity.of(30_000_000_000L),
            gasLimit = Quantity.of(21_000L),
            to = to,
            value = Quantity.of(1_000_000_000_000_000_000L),
            data = ByteArray(0),
        )
        val signed = signer.sign(tx, accountIndex = 0)
        // eth-account: Account.sign_transaction({type:2, chainId:1, nonce:9, maxPriorityFeePerGas:1.5gwei,
        //   maxFeePerGas:30gwei, gas:21000, to, value:1e18, accessList:[]}, anvil#0)
        assertEquals(
            "0x02f87301098459682f008506fc23ac008252089470997970c51812dc3a010c7d01b50e0d17dc79c8" +
                "880de0b6b3a764000080c080a03b4d50792c557066a6ed9f4c8f08ac567942ae78a61c2a6b87282ccaa14755d4" +
                "a05e0c4d4a31ed29a66b7d0074b7345b4ccbe26458cde8fea1ede2c3cc749882fd",
            signed.rawTransactionHex,
        )
        assertEquals("88917df26a386c29af4f64ea67576f3b9659488bfdd50cfc85ee1f64bd04d51f", Hex.encode(signed.transactionHash))
        assertEquals("3b4d50792c557066a6ed9f4c8f08ac567942ae78a61c2a6b87282ccaa14755d4", Hex.encode(signed.signature.r))
        assertEquals("5e0c4d4a31ed29a66b7d0074b7345b4ccbe26458cde8fea1ede2c3cc749882fd", Hex.encode(signed.signature.s))
        assertEquals(0, signed.yParity)
    }

    @Test
    fun nonce0_yParity1_matchesEthAccountRaw() {
        // Distinct y-parity (1) + an r whose top byte is non-zero — guards parity-bit + r/s ordering.
        val tx = Eip1559Transaction(
            chainId = 1L,
            nonce = Quantity.of(0L),
            maxPriorityFeePerGas = Quantity.of(1_000_000_000L),
            maxFeePerGas = Quantity.of(20_000_000_000L),
            gasLimit = Quantity.of(21_000L),
            to = to,
            value = Quantity.of(1_000_000_000_000_000_000L),
            data = ByteArray(0),
        )
        val signed = signer.sign(tx, accountIndex = 0)
        assertEquals(
            "0x02f8730180843b9aca008504a817c8008252089470997970c51812dc3a010c7d01b50e0d17dc79c8" +
                "880de0b6b3a764000080c001a00a3a2646d4ad968bb56317fdb15e17fb4d9deec0d03e8b6cb05e7125bda3006d" +
                "a026618ad17cc63cf1979ca75f850c536a073ec88ab1ab7c8db8b8eb129499d9ae",
            signed.rawTransactionHex,
        )
        assertEquals("9bab8d3e7893fd77088c164d2834ddcb9fbfa73c93bdee90e396e3e27141f1ba", Hex.encode(signed.transactionHash))
        assertEquals(1, signed.yParity)
    }
}
