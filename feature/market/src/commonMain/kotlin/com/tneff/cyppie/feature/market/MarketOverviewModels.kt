package com.tneff.cyppie.feature.market

import com.tneff.cyppie.market.MarketAsset
import com.tneff.cyppie.market.SpotPrice

/** A watchlist entry for the MD-3 market overview: the [asset] to price + its display [label]. */
data class WatchedAsset(val asset: MarketAsset, val label: String)

/**
 * One MD-3 overview row: the watched [asset]/[label], its current [spot] (price + 24h Δ; null if the
 * batch had no price for it), and a compact [sparkline] (recent closes). [sparkline] values are
 * render-Doubles (FR-6: financial values stay decimal-String/`Money` in `:market`; the parse-to-Double
 * is render-only). Visual layout is deferred to the UX MD-3 design — this is the design-agnostic data row.
 */
data class MarketOverviewRow(
    val asset: MarketAsset,
    val label: String,
    val spot: SpotPrice?,
    val sparkline: List<Double>,
)
