package com.tneff.cyppie.feature.market

/**
 * A single price-chart sample (UI-facing). Reconciled with Dev-2's `:market` `PricePoint` per ADR-0025
 * once `:market` merges — until then the UI scaffold uses this so it doesn't block on the data layer.
 */
data class MarketChartPoint(val epochSeconds: Long, val price: Double)

/** Chart time ranges (PRD-04 market detail). */
enum class MarketRange { DAY, WEEK, MONTH, YEAR }

/**
 * UI-facing market-data contract the [MarketViewModel] consumes. The app shell adapts Dev-2's `:market`
 * `MarketDataApi` (PRD-04 data layer) to this after the `:market` merge + the ADR-0025 PriceSource
 * relocation — keeping the UI module web-safe and free of the data-layer's transitive deps for now.
 */
interface MarketDataPort {
    /** Price series for [assetId] over [range] (display fiat resolved by the data layer). */
    suspend fun series(assetId: String, range: MarketRange): List<MarketChartPoint>
}
