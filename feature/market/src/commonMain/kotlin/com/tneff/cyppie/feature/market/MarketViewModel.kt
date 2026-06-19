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

/** Market detail UI state (PRD-04). [spot] drives the price/Δ header; [candles] the candlestick chart. */
sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Content(val candles: List<CandleBar>, val spot: SpotPrice?) : MarketUiState
    data object Error : MarketUiState
}

/**
 * KAN-131/KAN-133 (PRD-04) — drives the Market detail screen over `:market`'s [MarketDataApi] (the app
 * shell DI-s the live `BridgeMarketDataApi` binding when Dev-2 lands it; this consumes the **interface**).
 * Loads OHLC candles for the selected [range] (mapped to a CandleInterval+TimeRange) + the current spot.
 * FR-4 graceful (failure → [MarketUiState.Error], retryable). FR-6: prices stay decimal-String in the
 * data layer; only the chart parses OHLC to Double for pixel mapping (a candle with any unparseable
 * field is dropped).
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
                val candles = data.candles(asset, interval, timeRange).mapNotNull { c ->
                    val o = c.open.toDoubleOrNull(); val h = c.high.toDoubleOrNull()
                    val l = c.low.toDoubleOrNull(); val cl = c.close.toDoubleOrNull()
                    if (o != null && h != null && l != null && cl != null) {
                        CandleBar(c.openEpochSeconds, o, h, l, cl)
                    } else null
                }
                val spot = data.spotPrices(listOf(asset), vs)[asset]
                MarketUiState.Content(candles, spot)
            }.getOrElse { MarketUiState.Error }
        }
    }
}
