package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.RecoverableSignature
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.WalletKeyException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Vectors for [UserOpSigner] (KAN-140) — the ERC-4337 / EIP-7702 signing primitive. Uses the well-known
 * Hardhat/Anvil default mnemonic so the owner address at `m/44'/60'/0'/0/0` is fixed and the produced
 * signature can be recovered back to that owner.
 */
class UserOpSignerTest {

    private val mnemonic = "test test test test test test test test test test test junk"

    // Hardhat account #0 = the AA owner / SIWE identity at m/44'/60'/0'/0/0.
    private val owner = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val notOwner = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8") // account #1

    private fun signer(): UserOpSigner =
        UserOpSigner(EvmKeyManager(SeedSource.ofSeed(Mnemonic.of(mnemonic).toSeed())))

    // A backend-supplied 32-byte userOpHash (we never compute it here — the aa-trigger does).
    private val userOpHash = Keccak.keccak256("user-operation".encodeToByteArray())

    @Test
    fun signsRecoverable65ByteSignatureToOwner() {
        val sig = signer().sign(userOpHash, owner)

        assertEquals(UserOpSigner.SIGNATURE_LENGTH, sig.size) // r(32) ‖ s(32) ‖ v(1)
        val r = sig.copyOfRange(0, 32)
        val s = sig.copyOfRange(32, 64)
        val v = sig[64].toInt() and 0xFF
        assertTrue(v == 27 || v == 28, "v must be eth_sign-style 27/28, was $v") // valid secp256k1 → recId 0/1

        // The aa-trigger / EntryPoint ecrecover must land back on the disclosed owner.
        val recovered = RecoverableSignature(r, s, v - 27).recoverAddress(userOpHash)
        assertEquals(owner, recovered)
    }

    @Test
    fun isDeterministicForSameDigest() {
        // RFC-6979 deterministic ECDSA → byte-identical signature for identical input.
        assertContentEquals(signer().sign(userOpHash, owner), signer().sign(userOpHash, owner))
    }

    @Test
    fun samePrimitiveSignsADifferentDigest7702() {
        // EIP-7702 auth-tuple is a different digest, same signing path → a distinct, still-valid signature.
        val authTupleHash = Keccak.keccak256("eip7702-auth-tuple".encodeToByteArray())
        val sig = signer().sign(authTupleHash, owner)
        assertEquals(UserOpSigner.SIGNATURE_LENGTH, sig.size)
        val recovered = RecoverableSignature(
            sig.copyOfRange(0, 32), sig.copyOfRange(32, 64), (sig[64].toInt() and 0xFF) - 27,
        ).recoverAddress(authTupleHash)
        assertEquals(owner, recovered)
        assertTrue(!sig.contentEquals(signer().sign(userOpHash, owner))) // different digest → different sig
    }

    @Test
    fun rejectsNonOwnerNoBlindSigning() {
        // Signing account #0 derives to `owner`, not `notOwner` → fail closed before signing.
        assertFailsWith<WalletKeyException.SignerMismatch> { signer().sign(userOpHash, notOwner) }
    }

    @Test
    fun rejectsWrongHashLength() {
        assertFailsWith<WalletKeyException.InvalidSigningInput> { signer().sign(ByteArray(31), owner) }
        assertFailsWith<WalletKeyException.InvalidSigningInput> { signer().sign(ByteArray(33), owner) }
    }
}
