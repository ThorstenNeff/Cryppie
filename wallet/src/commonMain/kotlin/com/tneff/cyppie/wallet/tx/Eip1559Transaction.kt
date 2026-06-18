package com.tneff.cyppie.wallet.tx

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity

/**
 * An unsigned EIP-1559 (Type-2) dynamic-fee transaction for an EVM chain (Ethereum, Base).
 *
 * Field order matches the EIP-2718 typed-envelope payload
 * `[chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList]`.
 * Replay protection is the in-payload [chainId] (EIP-155 spirit; the legacy `v`-folding does not
 * apply to typed txs). The MVP uses an empty access list.
 */
data class Eip1559Transaction(
    /** EIP-155 chain id (Ethereum mainnet = 1, Base = 8453). */
    val chainId: Long,
    val nonce: Quantity,
    val maxPriorityFeePerGas: Quantity,
    val maxFeePerGas: Quantity,
    val gasLimit: Quantity,
    /** Recipient, or null for contract creation. */
    val to: EvmAddress?,
    val value: Quantity,
    /** Calldata (native transfer = empty; ERC-20 = [com.tneff.cyppie.wallet.abi.Erc20Abi] output). */
    val data: ByteArray = ByteArray(0),
) {
    init {
        require(chainId > 0) { "chainId must be positive" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Eip1559Transaction) return false
        return chainId == other.chainId && nonce == other.nonce &&
            maxPriorityFeePerGas == other.maxPriorityFeePerGas && maxFeePerGas == other.maxFeePerGas &&
            gasLimit == other.gasLimit && to == other.to && value == other.value &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var h = chainId.hashCode()
        h = 31 * h + nonce.hashCode()
        h = 31 * h + maxPriorityFeePerGas.hashCode()
        h = 31 * h + maxFeePerGas.hashCode()
        h = 31 * h + gasLimit.hashCode()
        h = 31 * h + (to?.hashCode() ?: 0)
        h = 31 * h + value.hashCode()
        h = 31 * h + data.contentHashCode()
        return h
    }

    companion object {
        /** EIP-2718 type byte for dynamic-fee transactions. */
        const val TYPE: Int = 0x02
    }
}
