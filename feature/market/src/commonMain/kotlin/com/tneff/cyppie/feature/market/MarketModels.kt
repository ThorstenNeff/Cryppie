package com.tneff.cyppie.feature.market

import com.tneff.cyppie.market.CandleInterval
import com.tneff.cyppie.market.TimeRange

/**
 * A candlestick render bar (UI-only). OHLC are `Double` for **pixel mapping only** (KAN-133) — financial
 * values stay decimal-String/`Money` in `:market` (FR-6); the VM parses `:market`'s decimal-String
 * `Candle` here just to draw bodies/wicks. [up] = close ≥ open (green vs red body).
 */
data class CandleBar(
    val openEpochSeconds: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
) {
    val up: Boolean get() = close >= open
}

/**
 * MD-1 token-detail market metrics (24h window). [high24h]/[low24h] keep the source decimal-String
 * (FR-6 — the value is the original, no float); [volume24h] is a render-aggregate Double (sum across the
 * window, null if no source reports volume). Visual layout deferred to the UX MD-1 design.
 */
data class MarketMetrics(val high24h: String?, val low24h: String?, val volume24h: Double?)

/** Chart time ranges (PRD-04 market detail) → a (CandleInterval, TimeRange) query against MarketDataApi. */
enum class MarketRange { DAY, WEEK, MONTH, YEAR }

private const val DAY_SECONDS = 86_400L

/** Maps a [MarketRange] to the `:market` query window ending at [nowEpochSeconds] + a sensible bar width. */
fun MarketRange.toQuery(nowEpochSeconds: Long): Pair<CandleInterval, TimeRange> = when (this) {
    MarketRange.DAY -> CandleInterval.M15 to TimeRange(nowEpochSeconds - DAY_SECONDS, nowEpochSeconds)
    MarketRange.WEEK -> CandleInterval.H1 to TimeRange(nowEpochSeconds - 7 * DAY_SECONDS, nowEpochSeconds)
    MarketRange.MONTH -> CandleInterval.H4 to TimeRange(nowEpochSeconds - 30 * DAY_SECONDS, nowEpochSeconds)
    MarketRange.YEAR -> CandleInterval.D1 to TimeRange(nowEpochSeconds - 365 * DAY_SECONDS, nowEpochSeconds)
}
