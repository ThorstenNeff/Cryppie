package com.tneff.cyppie.feature.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.market.MarketAsset
import com.tneff.cyppie.market.MarketDataApi
import com.tneff.cyppie.market.SpotPrice
import kotlinx.coroutines.launch

/** Market detail UI state (PRD-04). [spot] drives the price/Δ header; [points] the chart. */
sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Content(val points: List<MarketChartPoint>, val spot: SpotPrice?) : MarketUiState
    data object Error : MarketUiState
}

/**
 * KAN-131 (PRD-04) — drives the Market detail screen over `:market`'s [MarketDataApi] (the app shell
 * DI-s the live `BridgeMarketDataApi` binding when Dev-2 lands it; this consumes the **interface**).
 * Loads the price history for the selected [range] (mapped to a CandleInterval+TimeRange) + the current
 * spot. FR-4 graceful (failure → [MarketUiState.Error], retryable). FR-6: prices stay decimal-String in
 * the data layer; only the chart polyline parses to Double for pixel mapping.
 */
class MarketViewModel(
    private val asset: MarketAsset,
    private val vs: String,
    private val data: MarketDataApi,
    private val nowEpochSeconds: () -> Long,
) : ViewModel() {

    var range: MarketRange by mutableStateOf(MarketRange.DAY); private set
    var uiState: MarketUiState by mutableStateOf(MarketUiState.Loading); private set

    init { load() }

    fun selectRange(value: MarketRange) {
        if (value == range) return
        range = value
        load()
    }

    fun retry() = load()

    private fun load() {
        uiState = MarketUiState.Loading
        viewModelScope.launch {
            uiState = runCatching {
                val (interval, timeRange) = range.toQuery(nowEpochSeconds())
                val points = data.priceHistory(asset, vs, interval, timeRange).mapNotNull { p ->
                    p.priceDecimal.toDoubleOrNull()?.let { MarketChartPoint(p.epochSeconds, it) }
                }
                val spot = data.spotPrices(listOf(asset), vs)[asset]
                MarketUiState.Content(points, spot)
            }.getOrElse { MarketUiState.Error }
        }
    }
}
