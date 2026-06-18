package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity

/** Receive-screen data for an account on a chain: the address + its EIP-681 QR payload. */
data class ReceiveInfo(
    val address: EvmAddress,
    val chain: EvmChain,
    val eip681Uri: String,
)

/**
 * Aggregated balances for one account on one chain (wallet-home). [degraded] reflects the provider
 * state (FR-4 — surfaced so the UI can show a "degraded connection" hint). Token amounts are raw
 * (wei/base units); decimal formatting + fiat valuation are PRD-03.
 */
data class ChainBalances(
    val chain: EvmChain,
    val account: EvmAddress,
    val native: Quantity,
    val tokens: Map<EvmAddress, Quantity>,
    val degraded: Boolean,
)
