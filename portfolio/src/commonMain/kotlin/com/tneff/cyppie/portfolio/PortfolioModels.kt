package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity

/**
 * A fiat amount as a fixed-point decimal: value = [minorUnits] / 10^[scale] (e.g. scale=2 → cents).
 * Integer-based to keep valuation deterministic + test-checkable (FR-6) — no floating point.
 */
data class Money(val minorUnits: Long, val scale: Int, val currency: String) {

    /**
     * Sum of two amounts. Guards currency + scale equality (PO note: two `Money` with the same value
     * but a different [scale] are unequal under the data-class `equals`, so summing must not silently
     * mix scales). Normalize first with [atScale] if scales differ.
     */
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "currency mismatch: $currency vs ${other.currency}" }
        require(scale == other.scale) { "scale mismatch: $scale vs ${other.scale} (normalize with atScale first)" }
        // Saturating add (KAN-100 L1, defensive): real values are bounded (spam/dust filtered before
        // valuation), but capped-absurd inputs must never silently wrap a portfolio total.
        val sum = minorUnits + other.minorUnits
        val saturated = if ((minorUnits xor sum) and (other.minorUnits xor sum) < 0L) {
            if (minorUnits > 0L) Long.MAX_VALUE else Long.MIN_VALUE
        } else {
            sum
        }
        return Money(saturated, scale, currency)
    }

    /** Re-scales to [newScale] (only upscaling is exact; downscaling truncates). */
    fun atScale(newScale: Int): Money = when {
        newScale == scale -> this
        newScale > scale -> Money(minorUnits * pow10(newScale - scale), newScale, currency)
        else -> Money(minorUnits / pow10(scale - newScale), newScale, currency)
    }

    companion object {
        fun zero(currency: String, scale: Int = 2): Money = Money(0, scale, currency)

        private fun pow10(n: Int): Long {
            var r = 1L
            repeat(n) { r *= 10 }
            return r
        }
    }
}

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
