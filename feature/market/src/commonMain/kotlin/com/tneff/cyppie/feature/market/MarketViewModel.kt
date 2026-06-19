package com.tneff.cyppie.feature.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.market.Candle
import com.tneff.cyppie.market.CandleInterval
import com.tneff.cyppie.market.MarketAsset
import com.tneff.cyppie.market.MarketDataApi
import com.tneff.cyppie.market.SpotPrice
import com.tneff.cyppie.market.TimeRange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Market detail UI state (PRD-04). [spot] drives the price/Δ header; [candles] the candlestick chart;
 * [metrics] the MD-1 24h market metrics (best-effort, may be null).
 */
sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Content(val candles: List<CandleBar>, val spot: SpotPrice?, val metrics: MarketMetrics?) : MarketUiState
    data object Error : MarketUiState
}

private const val METRICS_WINDOW_SECONDS = 86_400L // 24h

/**
 * KAN-131/133/134 (PRD-04) — drives the Market detail screen (MD-1) over `:market`'s [MarketDataApi]
 * (the app shell DI-s the live `BridgeMarketDataApi` when Dev-2 lands it; this consumes the **interface**).
 * Concurrently loads: the selected-[range] OHLC candles (chart), the current spot (price/Δ header), and a
 * 24h window for [MarketMetrics] (high/low/volume). FR-4: chart/spot failure → [MarketUiState.Error]
 * (retryable); metrics are **best-effort** (failure → null, screen still renders). FR-6: prices stay
 * decimal-String in the data layer; only the chart parses OHLC to Double for pixel mapping (a candle with
 * any unparseable field is dropped), and metrics keep high/low as the source String.
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
                val now = nowEpochSeconds()
                val (interval, timeRange) = range.toQuery(now)
                coroutineScope {
                    val chart = async {
                        data.candles(asset, interval, timeRange).mapNotNull { c ->
                            val o = c.open.toDoubleOrNull(); val h = c.high.toDoubleOrNull()
                            val l = c.low.toDoubleOrNull(); val cl = c.close.toDoubleOrNull()
                            if (o != null && h != null && l != null && cl != null) {
                                CandleBar(c.openEpochSeconds, o, h, l, cl)
                            } else null
                        }
                    }
                    val spotDeferred = async { data.spotPrices(listOf(asset), vs)[asset] }
                    // Metrics best-effort: a 24h-window failure leaves them null, the screen still renders.
                    val metrics = async {
                        runCatching {
                            metricsFrom(data.candles(asset, CandleInterval.H1, TimeRange(now - METRICS_WINDOW_SECONDS, now)))
                        }.getOrNull()
                    }
                    MarketUiState.Content(chart.await(), spotDeferred.await(), metrics.await())
                }
            }.getOrElse { MarketUiState.Error }
        }
    }

    /** 24h metrics from a candle window: high/low keep the source String (FR-6); volume is a render sum. */
    private fun metricsFrom(candles: List<Candle>): MarketMetrics {
        if (candles.isEmpty()) return MarketMetrics(null, null, null)
        val high = candles.maxByOrNull { it.high.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }?.high
        val low = candles.minByOrNull { it.low.toDoubleOrNull() ?: Double.POSITIVE_INFINITY }?.low
        val volumes = candles.mapNotNull { it.volume?.toDoubleOrNull() }
        return MarketMetrics(high, low, if (volumes.isEmpty()) null else volumes.sum())
    }
}
