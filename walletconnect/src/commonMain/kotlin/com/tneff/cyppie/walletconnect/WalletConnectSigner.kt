package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.tx.Eip1559Transaction
import com.tneff.cyppie.wallet.tx.EvmTransactionSigner
import com.tneff.cyppie.wallet.tx.SignedTransaction

/**
 * Fulfils approved WalletConnect signing requests using L1/L2 (`:wallet`). All signing happens
 * on-device; the private key never leaves [EvmKeyManager]. Pure/synchronous — the controller calls
 * this only after the user approves the fully-disclosed request ([disclose], FR-3).
 */
class WalletConnectSigner(private val keyManager: EvmKeyManager) {

    private val transactionSigner = EvmTransactionSigner(keyManager)

    /**
     * `personal_sign` (EIP-191): signs `keccak256("\x19Ethereum Signed Message:\n" + len + message)`.
     * Returns the 65-byte `r ‖ s ‖ v` signature with `v = 27 + recId`.
     */
    fun personalSign(message: ByteArray, accountIndex: Int): ByteArray {
        val prefix = "Ethereum Signed Message:\n${message.size}".encodeToByteArray()
        val digest = Keccak.keccak256(prefix + message)
        val signature = keyManager.sign(accountIndex, digest)
        return signature.r + signature.s + byteArrayOf((27 + signature.recId).toByte())
    }

    /**
     * `eth_sendTransaction`: signs the (orchestration-completed: nonce/fees filled) [transaction];
     * returns the broadcast-ready [SignedTransaction] (the app/L3 submits it and returns the hash).
     */
    fun signTransaction(transaction: Eip1559Transaction, accountIndex: Int): SignedTransaction =
        transactionSigner.sign(transaction, accountIndex)
}
