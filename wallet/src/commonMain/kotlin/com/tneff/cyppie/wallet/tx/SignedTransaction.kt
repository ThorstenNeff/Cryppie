package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.wallet.RecoverableSignature

/**
 * The result of signing an [Eip1559Transaction].
 *
 * [rawTransaction] is the EIP-2718 broadcast payload (`0x02 || rlp([...fields, yParity, r, s])`)
 * ready for `eth_sendRawTransaction`; [transactionHash] is its keccak-256 (the on-chain tx hash).
 */
class SignedTransaction(
    val rawTransaction: ByteArray,
    val transactionHash: ByteArray,
    val signature: RecoverableSignature,
    /** Signature y-parity (0/1) — the typed-tx `v`. */
    val yParity: Int,
) {
    /** `0x`-prefixed raw transaction, as passed to `eth_sendRawTransaction`. */
    val rawTransactionHex: String
        get() = "0x" + buildString(rawTransaction.size * 2) {
            for (b in rawTransaction) {
                val v = b.toInt() and 0xFF
                append("0123456789abcdef"[v ushr 4])
                append("0123456789abcdef"[v and 0x0F])
            }
        }
}
