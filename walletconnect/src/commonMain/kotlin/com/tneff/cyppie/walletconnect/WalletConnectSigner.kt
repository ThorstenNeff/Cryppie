package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.Eip191
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.tx.EvmTransactionSigner
import com.tneff.cyppie.wallet.tx.SignedTransaction

/**
 * Fulfils approved WalletConnect signing requests using L1/L2 (`:wallet`). All signing happens
 * on-device; the private key never leaves [EvmKeyManager]. Pure/synchronous — the controller calls
 * this only after the user approves the fully-disclosed request ([disclose], FR-3).
 *
 * Every call verifies that [EvmKeyManager.deriveAddress] for the given `accountIndex` equals the
 * request's signer address (FR-3 — the wallet must sign with the account the user was shown).
 */
class WalletConnectSigner(private val keyManager: EvmKeyManager) {

    private val transactionSigner = EvmTransactionSigner(keyManager)

    /**
     * `personal_sign` (EIP-191): signs the shared [Eip191.personalSignDigest] of the message.
     * Returns the 65-byte `r ‖ s ‖ v` signature with `v = 27 + recId`.
     */
    fun personalSign(request: WcSigningRequest.PersonalSign, accountIndex: Int): ByteArray {
        requireSignerMatches(accountIndex, request.address)
        val digest = Eip191.personalSignDigest(request.message)
        val signature = keyManager.sign(accountIndex, digest)
        return signature.r + signature.s + byteArrayOf((27 + signature.recId).toByte())
    }

    /**
     * `eth_sendTransaction`: signs the (orchestration-completed: nonce/fees filled) transaction;
     * returns the broadcast-ready [SignedTransaction] (the app/L3 submits it and returns the hash).
     */
    fun signTransaction(request: WcSigningRequest.SendTransaction, accountIndex: Int): SignedTransaction {
        requireSignerMatches(accountIndex, request.address)
        return transactionSigner.sign(request.transaction, accountIndex)
    }

    /**
     * `eth_signTypedData_v4` (EIP-712): signs the typed-data digest. Returns the 65-byte
     * `r ‖ s ‖ v` signature with `v = 27 + recId`.
     */
    fun signTypedDataV4(request: WcSigningRequest.SignTypedDataV4, accountIndex: Int): ByteArray {
        requireSignerMatches(accountIndex, request.address)
        val digest = Eip712.encode(request.typedDataJson)
        val signature = keyManager.sign(accountIndex, digest)
        return signature.r + signature.s + byteArrayOf((27 + signature.recId).toByte())
    }

    /** FR-3 safeguard: the signing account must match the address shown in the request. */
    private fun requireSignerMatches(accountIndex: Int, expected: EvmAddress) {
        val actual = keyManager.deriveAddress(accountIndex)
        if (actual != expected) {
            throw WalletConnectException.UnsupportedRequest(
                "Account #$accountIndex ($actual) does not match the request signer $expected",
            )
        }
    }
}
