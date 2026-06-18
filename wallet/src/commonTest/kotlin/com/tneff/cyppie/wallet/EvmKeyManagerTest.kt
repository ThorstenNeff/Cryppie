package com.tneff.cyppie.wallet

import com.tneff.cyppie.evm.Keccak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Sanity vectors for the L1 key/account layer. The authoritative vector suite is owned by the
 * Test agent (KAN-57); these guard the full pipeline (BIP-39 seed → BIP-44 derive → secp256k1
 * pubkey → keccak → EIP-55) against the well-known Hardhat default accounts.
 */
class EvmKeyManagerTest {

    // Hardhat / Anvil default mnemonic and its first accounts on m/44'/60'/0'/0/i.
    private val mnemonic = "test test test test test test test test test test test junk"

    private fun manager(): EvmKeyManager =
        EvmKeyManager(SeedSource.ofSeed(Mnemonic.of(mnemonic).toSeed()))

    @Test
    fun derivesWellKnownHardhatAddresses() {
        val km = manager()
        assertEquals("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", km.deriveAddress(0).value)
        assertEquals("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", km.deriveAddress(1).value)
        assertEquals("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC", km.deriveAddress(2).value)
    }

    @Test
    fun accountCarriesIndexAndBip44Path() {
        val acc = manager().deriveAccount(5)
        assertEquals(5, acc.index)
        assertEquals("m/44'/60'/0'/0/5", acc.path)
    }

    @Test
    fun signIsRecoverableAndDeterministic() {
        val km = manager()
        val hash = Keccak.keccak256("cyppie".encodeToByteArray())
        val sig = km.sign(0, hash)
        assertEquals(32, sig.r.size)
        assertEquals(32, sig.s.size)
        assertTrue(sig.recId in 0..3)
        assertEquals(64, sig.toCompact().size)
        // RFC-6979 deterministic ECDSA → identical signature for identical input.
        assertEquals(sig, km.sign(0, hash))
    }

    @Test
    fun rejectsWrongHashLength() {
        assertFailsWith<WalletKeyException.InvalidSigningInput> { manager().sign(0, ByteArray(31)) }
    }

    @Test
    fun rejectsNegativeIndex() {
        assertFailsWith<WalletKeyException.InvalidDerivationIndex> { manager().deriveAccount(-1) }
    }
}
