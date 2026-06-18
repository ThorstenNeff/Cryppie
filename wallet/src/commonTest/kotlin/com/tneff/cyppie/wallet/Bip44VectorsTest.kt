package com.tneff.cyppie.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-57 — authoritative **BIP-44 → EVM address** known-answer vectors (ADR-0014 audit gate).
 *
 * Exercises the full pipeline (BIP-39 seed → BIP-44 `m/44'/60'/0'/0/i` → secp256k1 pubkey →
 * keccak256 → EIP-55) against two independent, well-known public test mnemonics, plus the negative
 * cases against the sealed [WalletKeyException]. Verified against authoritative sources 2026-06-18
 * (Hardhat default accounts; the canonical BIP-39 "abandon…about" phrase).
 *
 * Security (§4.3): only public throwaway test mnemonics — never a real seed; no key material is
 * asserted or logged. Empty passphrase (the iancoleman/Hardhat default).
 */
class Bip44VectorsTest {

    private val hardhat = "test test test test test test test test test test test junk"
    private val abandon = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private fun managerFor(mnemonic: String): EvmKeyManager =
        EvmKeyManager(SeedSource.ofSeed(Mnemonic.of(mnemonic).toSeed()))

    @Test
    fun hardhatDefaultAccounts_i0to2() {
        val km = managerFor(hardhat)
        assertEquals("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", km.deriveAddress(0).value)
        assertEquals("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", km.deriveAddress(1).value)
        assertEquals("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC", km.deriveAddress(2).value)
    }

    @Test
    fun abandonAboutAccount0() {
        // Independent second mnemonic (BIP-39 all-zero-entropy canonical phrase).
        assertEquals(
            "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
            managerFor(abandon).deriveAddress(0).value,
        )
    }

    @Test
    fun accountCarriesIndexAndBip44Path() {
        val acc = managerFor(hardhat).deriveAccount(7)
        assertEquals(7, acc.index)
        assertEquals("m/44'/60'/0'/0/7", acc.path)
    }

    @Test
    fun rejectsInvalidMnemonics() {
        // 12× "abandon" → valid words but wrong BIP-39 checksum.
        assertFailsWith<WalletKeyException.InvalidMnemonic> {
            Mnemonic.of("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon")
        }
        // Wrong word count (11 words).
        assertFailsWith<WalletKeyException.InvalidMnemonic> {
            Mnemonic.of("test test test test test test test test test test junk")
        }
        // A word outside the BIP-39 wordlist.
        assertFailsWith<WalletKeyException.InvalidMnemonic> {
            Mnemonic.of("zzzz zzzz zzzz zzzz zzzz zzzz zzzz zzzz zzzz zzzz zzzz zzzz")
        }
    }

    @Test
    fun rejectsNegativeAccountIndex() {
        assertFailsWith<WalletKeyException.InvalidDerivationIndex> { managerFor(hardhat).deriveAccount(-1) }
    }
}
