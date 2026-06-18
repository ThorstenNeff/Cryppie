package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress

/** A resolved ERC-20 token (KAN-83). [decimals] is the on-chain `decimals()`; amounts stay raw. */
data class Erc20Token(
    val address: EvmAddress,
    val chain: EvmChain,
    val symbol: String,
    val decimals: Int,
)

/** Outcome of resolving an ERC-20 by contract address (KAN-49 states). */
sealed interface TokenResolution {
    /** A valid ERC-20 was found and its `symbol`/`decimals` read. */
    data class Resolved(val token: Erc20Token) : TokenResolution

    /** The address is a valid EIP-55 address but not an ERC-20 (EOA, or no `symbol`/`decimals`). */
    data object NotErc20 : TokenResolution

    /** The token couldn't be resolved because the RPC was unreachable (retryable). */
    data object NetworkError : TokenResolution
}
