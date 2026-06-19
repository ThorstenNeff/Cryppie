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
    data class Content(
        val candles: List<CandleBar>,
        val spot: SpotPrice?,
        val metrics: MarketMetrics?,
        val freshness: Freshness?,
    ) : MarketUiState
    data object Error : MarketUiState
}

/** Spot freshness (SPEC §2). null = no spot. [Delayed.stale] flips the `mkt_stale` warning vs `mkt_last_updated`. */
sealed interface Freshness {
    data object Live : Freshness
    data class Delayed(val label: String, val stale: Boolean) : Freshness
}

/** Freshness from a last-updated stamp (shared by MD-1 + MD-3): no spot→null, none→Live, else Delayed
 *  (stale past the threshold). [lastUpdatedEpochSeconds] is the (oldest, for a list) update stamp. */
internal fun freshnessOf(lastUpdatedEpochSeconds: Long?, hasSpot: Boolean, now: Long): Freshness? {
    if (!hasSpot) return null
    val updated = lastUpdatedEpochSeconds ?: return Freshness.Live
    val agoMin = ((now - updated) / 60).coerceAtLeast(0)
    return Freshness.Delayed(label = "${agoMin}m", stale = now - updated > STALE_THRESHOLD_SECONDS)
}

private const val STALE_THRESHOLD_SECONDS = 300L // spot older than 5 min → "delayed" (SPEC NFR-2 stale-served)

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
                    // Cap/supply/volume via MarketDataApi.marketStats (KAN-132, CoinGecko /coins/markets).
                    // Best-effort: a failure (or no CoinGecko key → 503) leaves them null, the screen renders.
                    val metrics = async {
                        runCatching {
                            data.marketStats(listOf(asset), vs)[asset]?.let {
                                MarketMetrics(marketCap = it.marketCap, volume24h = it.volume24h, circulatingSupply = it.circulatingSupply)
                            }
                        }.getOrNull()
                    }
                    val spot = spotDeferred.await()
                    MarketUiState.Content(chart.await(), spot, metrics.await(), freshnessOf(spot?.lastUpdatedEpochSeconds, spot != null, now))
                }
            }.getOrElse { MarketUiState.Error }
        }
    }
}
