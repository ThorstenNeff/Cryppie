package com.tneff.cyppie.feature.market

import com.tneff.cyppie.market.CandleInterval
import com.tneff.cyppie.market.TimeRange

/**
 * A chart render point (UI-only). [price] is a `Double` for **pixel mapping only** — financial values
 * stay String/`Money` in `:market` (FR-6); the VM converts `:market`'s decimal-String `PricePoint` here
 * just to draw the polyline.
 */
data class MarketChartPoint(val epochSeconds: Long, val price: Double)

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
