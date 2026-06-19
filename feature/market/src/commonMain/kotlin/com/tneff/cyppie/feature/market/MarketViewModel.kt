package com.tneff.cyppie.feature.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** Market detail UI state (PRD-04). */
sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Content(val points: List<MarketChartPoint>) : MarketUiState
    data object Error : MarketUiState
}

/**
 * KAN (PRD-04) — drives the Market detail screen over a [MarketDataPort] (adapted from Dev-2's `:market`
 * `MarketDataApi` by the app shell, ADR-0025). Loads a price series for the selected [range]; FR-4
 * graceful (load failure → [MarketUiState.Error], retryable). Scaffold: wired to the live data layer
 * after the `:market` merge + PriceSource relocation.
 */
class MarketViewModel(
    private val assetId: String,
    private val data: MarketDataPort,
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
            uiState = runCatching { MarketUiState.Content(data.series(assetId, range)) }
                .getOrElse { MarketUiState.Error }
        }
    }
}
