package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.WalletKeyException

/**
 * Signs ERC-4337 **user-operation hashes** (and the one-time EIP-7702 authorization-tuple hash) on-device
 * for the AA-Foundation (PRD-05, KAN-140 / Epic KAN-138).
 *
 * The bundler side (`aa-trigger`) builds the UserOp and returns the ready 32-byte `userOpHash` (from
 * `/v1/userop/build`); this signer **never packs/encodes a UserOp** and **never builds the Kernel-validator
 * envelope**. It raw-signs the supplied digest and returns the 65-byte recoverable `r ‖ s ‖ v`
 * (`v = 27 + recId`, eth_sign-style) — `aa-trigger` wraps that into the Kernel ECDSA-validator format. The
 * same primitive serves the EIP-7702 auth-tuple (a different digest, identical signing). The exact
 * Kernel-wrapping / `v` bytes are finalized backend-side (Inc.2) and don't change this contract.
 *
 * Security (mirrors [EvmTransactionSigner] / WalletConnectSigner):
 *  - Delegates to L1 [EvmKeyManager.sign]; the private key is derived, used, and zeroized inside the
 *    [com.tneff.cyppie.wallet.SeedSource] scope — fresh-seed-per-signature, never returned/persisted.
 *  - **No blind signing**: `expectedOwner` is mandatory and must equal the AA owner account's address (the
 *    SIWE identity at `m/44'/60'/0'/0/0`), else [WalletKeyException.SignerMismatch].
 *
 * Pure/synchronous; the caller owns threading. Narrow surface for the mandatory review/audit gate.
 */
class UserOpSigner(private val keyManager: EvmKeyManager) {

    /**
     * Signs the backend-supplied 32-byte [userOpHash] with the AA owner account [accountIndex] (default
     * [OWNER_ACCOUNT_INDEX] = the SIWE identity). Verifies the account derives to [expectedOwner] (no blind
     * signing) first, then returns the 65-byte recoverable signature `r ‖ s ‖ v` (`v = 27 + recId`).
     *
     * @throws WalletKeyException.InvalidSigningInput if [userOpHash] is not exactly 32 bytes.
     * @throws WalletKeyException.SignerMismatch if account [accountIndex] does not derive to [expectedOwner].
     */
    fun sign(userOpHash: ByteArray, expectedOwner: EvmAddress, accountIndex: Int = OWNER_ACCOUNT_INDEX): ByteArray {
        if (userOpHash.size != 32) {
            throw WalletKeyException.InvalidSigningInput("userOpHash must be 32 bytes, was ${userOpHash.size}")
        }
        requireSignerMatches(accountIndex, expectedOwner)
        val signature = keyManager.sign(accountIndex, userOpHash)
        return signature.r + signature.s + byteArrayOf((27 + signature.recId).toByte())
    }

    /** No-blind-signing gate: the signing account must derive to [expectedOwner] (the disclosed AA owner). */
    private fun requireSignerMatches(accountIndex: Int, expectedOwner: EvmAddress) {
        val actual = keyManager.deriveAddress(accountIndex)
        if (actual != expectedOwner) {
            throw WalletKeyException.SignerMismatch(
                "AA owner account #$accountIndex ($actual) does not match the expected owner $expectedOwner",
            )
        }
    }

    companion object {
        /** The AA owner = the SIWE identity at `m/44'/60'/0'/0/0` (PRD-05 / Epic KAN-138). */
        const val OWNER_ACCOUNT_INDEX: Int = 0

        /** Recoverable signature byte length: `r(32) ‖ s(32) ‖ v(1)`. */
        const val SIGNATURE_LENGTH: Int = 65
    }
}
