package com.tneff.cyppie.market

import com.tneff.cyppie.evm.EvmAddress

/**
 * A market-data asset reference (PRD-04 §4 `AssetRef`). Independent of `:portfolio`'s `PortfolioToken`
 * — this module is additive and does not touch PRD-03 (the `PriceSource` relocation is a held follow-up).
 */
sealed interface MarketAsset {
    val chainId: Long

    /** The chain's native coin (ETH on chainId 1, etc.). */
    data class Native(override val chainId: Long) : MarketAsset

    /** An ERC-20 token addressed by [contract] on [chainId]. */
    data class Erc20(override val chainId: Long, val contract: EvmAddress) : MarketAsset
}

/** Standard candle timeframe. [seconds] is the bar width (also the history sampling interval). */
enum class CandleInterval(val seconds: Long) {
    M1(60), M5(300), M15(900), H1(3_600), H4(14_400), D1(86_400)
}

/** A half-open historical window in epoch seconds. */
data class TimeRange(val fromEpochSeconds: Long, val toEpochSeconds: Long)

/**
 * One OHLC candle. Prices/volume are kept as **decimal strings** for precision (FR-6) — the consumer
 * parses them big-int-disciplined, never via float. [volume] is null for sources that omit it (e.g.
 * CoinGecko `/ohlc`). [openEpochSeconds] is the bar's open time.
 */
data class Candle(
    val openEpochSeconds: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String? = null,
)

/** A spot price in [vs] fiat (decimal string), with optional 24h change percent and last-updated stamp. */
data class SpotPrice(
    val priceDecimal: String,
    val vs: String,
    val change24hPct: String? = null,
    val lastUpdatedEpochSeconds: Long? = null,
)

/** A single historical price sample (decimal string + epoch seconds). */
data class PricePoint(val epochSeconds: Long, val priceDecimal: String)

/**
 * Market-wide statistics for one asset (MD-1, KAN-132). All figures are **decimal strings** for precision
 * (FR-6) — never parsed via float. Any field is null when the source omits it. [vs] is the fiat the
 * [marketCap]/[volume24h] are denominated in; [circulatingSupply] is a token count (fiat-independent).
 */
data class MarketStats(
    val marketCap: String? = null,
    val circulatingSupply: String? = null,
    val volume24h: String? = null,
    val vs: String,
)
