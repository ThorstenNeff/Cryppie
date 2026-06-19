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
