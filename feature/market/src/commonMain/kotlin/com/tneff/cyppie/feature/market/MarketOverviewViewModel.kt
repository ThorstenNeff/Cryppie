package com.tneff.cyppie.feature.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.market.CandleInterval
import com.tneff.cyppie.market.MarketDataApi
import com.tneff.cyppie.market.TimeRange
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** MD-3 market-overview UI state (PRD-04). [freshness] drives the header stale/live badge (SPEC §2). */
sealed interface MarketOverviewUiState {
    data object Loading : MarketOverviewUiState
    data class Content(val rows: List<MarketOverviewRow>, val freshness: Freshness?) : MarketOverviewUiState
    data object Error : MarketOverviewUiState
}

private const val SPARKLINE_WINDOW_SECONDS = 86_400L // last 24h

/**
 * MD-3 (PRD-04) — drives the market overview over `:market`'s [MarketDataApi] (interface; the app shell
 * DI-s Dev-2's live `BridgeMarketDataApi` later). One batched [MarketDataApi.spotPrices] call prices the
 * whole [watched] list (price + 24h Δ); a per-asset [MarketDataApi.priceHistory] (concurrent) feeds each
 * mini-sparkline. The sparkline is **best-effort** — a per-asset history failure leaves it empty, the row
 * still shows the spot (FR-4 graceful). A failed batch spot → [MarketOverviewUiState.Error]. FR-6: prices
 * stay decimal-String in the data layer; only the sparkline parses to Double at the render edge. Visual
 * layout/polish is deferred to the UX MD-3 design — this is the design-agnostic data/VM scaffold.
 */
class MarketOverviewViewModel(
    private val watched: List<WatchedAsset>,
    private val vs: String,
    private val data: MarketDataApi,
    private val nowEpochSeconds: () -> Long,
) : ViewModel() {

    var uiState: MarketOverviewUiState by mutableStateOf(MarketOverviewUiState.Loading); private set

    init { load() }

    fun retry() = load()

    private fun load() {
        uiState = MarketOverviewUiState.Loading
        viewModelScope.launch {
            uiState = runCatching {
                val now = nowEpochSeconds()
                val window = TimeRange(now - SPARKLINE_WINDOW_SECONDS, now)
                // Batched spot for the whole watchlist (one upstream call); failure → Error.
                val spots = data.spotPrices(watched.map { it.asset }, vs)
                // Per-asset sparkline, concurrent + best-effort (failure → empty, row still renders).
                val rows = coroutineScope {
                    watched.map { w ->
                        async {
                            val sparkline = runCatching {
                                data.priceHistory(w.asset, vs, CandleInterval.H1, window)
                                    .mapNotNull { it.priceDecimal.toDoubleOrNull() }
                            }.getOrElse { emptyList() }
                            MarketOverviewRow(w.asset, w.label, spots[w.asset], sparkline)
                        }
                    }.awaitAll()
                }
                // Freshness from the oldest spot stamp across the list (header stale/live badge, SPEC §2).
                val stamps = rows.mapNotNull { it.spot?.lastUpdatedEpochSeconds }
                val freshness = freshnessOf(stamps.minOrNull(), hasSpot = rows.any { it.spot != null }, now = now)
                MarketOverviewUiState.Content(rows, freshness)
            }.getOrElse { MarketOverviewUiState.Error }
        }
    }
}
