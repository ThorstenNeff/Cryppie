package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.evm.rlp.Rlp
import com.tneff.cyppie.evm.rlp.RlpItem
import com.tneff.cyppie.wallet.EvmCrypto
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.WalletKeyException

/**
 * Builds and signs EIP-1559 (Type-2) transactions (ADR-0014). Signing delegates to L1
 * ([EvmKeyManager.sign]) over the keccak-256 of the typed-envelope payload; the private key never
 * enters L2. The typed-tx `v` is the signature's y-parity (`recId`, 0/1) — no EIP-155 folding.
 *
 * Pure/synchronous; the caller owns threading. The narrow surface ([signingHash], [sign]) keeps the
 * encoder isolated for the mandatory review/audit gate.
 */
class EvmTransactionSigner(private val keyManager: EvmKeyManager) {

    /** keccak-256 of `0x02 || rlp(unsignedFields)` — the message L1 signs. */
    fun signingHash(tx: Eip1559Transaction): ByteArray =
        Keccak.keccak256(typedPayload(unsignedFields(tx)))

    /** Signs [tx] with account [accountIndex] and returns the broadcast-ready raw transaction. */
    fun sign(tx: Eip1559Transaction, accountIndex: Int): SignedTransaction {
        val hash = signingHash(tx)
        val sig = keyManager.sign(accountIndex, hash)
        // Type-2 requires a plain y-parity bit; a valid secp256k1 signature yields recId 0 or 1.
        if (sig.recId != 0 && sig.recId != 1) {
            throw WalletKeyException.InvalidSigningInput("Unexpected recovery id ${sig.recId} for typed tx")
        }
        val signedFields = unsignedFields(tx) + listOf(
            RlpItem.Str(Quantity.of(sig.recId.toLong()).toMinimalBytes()),
            RlpItem.Str(Quantity.ofBytes(sig.r).toMinimalBytes()),
            RlpItem.Str(Quantity.ofBytes(sig.s).toMinimalBytes()),
        )
        val raw = typedPayload(signedFields)
        return SignedTransaction(
            rawTransaction = raw,
            transactionHash = Keccak.keccak256(raw),
            signature = sig,
            yParity = sig.recId,
        )
    }

    /** The nine unsigned EIP-1559 fields, in canonical order. */
    private fun unsignedFields(tx: Eip1559Transaction): List<RlpItem> = listOf(
        RlpItem.Str(Quantity.of(tx.chainId).toMinimalBytes()),
        RlpItem.Str(tx.nonce.toMinimalBytes()),
        RlpItem.Str(tx.maxPriorityFeePerGas.toMinimalBytes()),
        RlpItem.Str(tx.maxFeePerGas.toMinimalBytes()),
        RlpItem.Str(tx.gasLimit.toMinimalBytes()),
        RlpItem.Str(tx.to?.bytes ?: ByteArray(0)),
        RlpItem.Str(tx.value.toMinimalBytes()),
        RlpItem.Str(tx.data),
        RlpItem.Lst(emptyList()), // accessList (empty in MVP)
    )

    /** `0x02 || rlp(list(fields))`. */
    private fun typedPayload(fields: List<RlpItem>): ByteArray =
        byteArrayOf(Eip1559Transaction.TYPE.toByte()) + Rlp.encode(RlpItem.Lst(fields))

    /** Recovers the signer address from a produced [signed] tx — used to self-verify signing. */
    fun recoverSigner(tx: Eip1559Transaction, signed: SignedTransaction): EvmAddress {
        val pub = EvmCrypto.recoverPublicKey(signingHash(tx), signed.signature)
        return EvmAddress.fromPublicKey(pub)
    }
}
