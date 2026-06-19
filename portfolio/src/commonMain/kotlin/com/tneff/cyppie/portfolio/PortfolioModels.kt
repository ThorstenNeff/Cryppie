package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.market.Money

// Money relocated to :market (ADR-0025, single-source money/pricing foundation) — imported above.

/** A token the portfolio tracks — native (ETH) when [contract] is null, else an ERC-20. */
data class PortfolioToken(
    val chainId: Long,
    val contract: EvmAddress?,
    val symbol: String,
    val decimals: Int,
) {
    val isNative: Boolean get() = contract == null
}

/** A raw balance of [token] held by [account]; [value] is the fiat valuation once priced (null until). */
data class Holding(
    val account: EvmAddress,
    val token: PortfolioToken,
    val rawBalance: Quantity,
    val value: Money? = null,
)

/** One slice of the asset allocation; [fractionBps] is basis points (sums to 10_000 across a portfolio). */
data class AllocationSlice(
    val token: PortfolioToken,
    val value: Money,
    val fractionBps: Int,
)

/**
 * Aggregated portfolio across accounts × chains (PRD-03). Headline metrics carry their FR-9
 * confidence: [totalValue] / [change24h] / [allocation] are robust; P&L and the rich/time-series
 * metrics (Slice P4) attach as [Metric.approximate]. Null metrics mean "not yet computed / unpriced".
 */
data class Portfolio(
    val holdings: List<Holding>,
    val totalValue: Metric<Money>? = null,
    val change24h: Metric<Money>? = null,
    val allocation: List<AllocationSlice> = emptyList(),
)
