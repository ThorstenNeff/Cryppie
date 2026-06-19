package com.tneff.cyppie.feature.portfolio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.portfolio.Metric
import com.tneff.cyppie.portfolio.Money
import com.tneff.cyppie.portfolio.Portfolio
import kotlinx.coroutines.launch

/**
 * What PF-1 renders: the priced [portfolio] (robust total / 24h / allocation) plus the optional
 * unrealized [pnl] — an `Approximate` rich metric (FR-9) the screen surfaces with an "≈" caveat.
 */
data class PortfolioOverview(
    val portfolio: Portfolio,
    val pnl: Metric<Money>? = null,
)

/** PF-1 Overview UI state. */
sealed interface PortfolioOverviewUiState {
    data object Loading : PortfolioOverviewUiState
    data class Content(val overview: PortfolioOverview) : PortfolioOverviewUiState
    data object Empty : PortfolioOverviewUiState
    data object Error : PortfolioOverviewUiState
}

/**
 * PF-1 Portfolio Overview flow controller (PRD-03). Loads a [PortfolioOverview] through the injected
 * [loadOverview] suspend seam — deliberately **not** a `:walletcore` type, so this module (and the
 * screen) stay web-capable; the app shell binds [loadOverview] to the platform assembler (native →
 * `PortfolioService`, web → the address-based portfolio fetch). Empty holdings → [Empty], failures →
 * [Error] (no crash). Mirrors `WalletHomeViewModel`.
 */
class PortfolioOverviewViewModel(
    private val loadOverview: suspend () -> PortfolioOverview,
) : ViewModel() {

    var uiState: PortfolioOverviewUiState by mutableStateOf(PortfolioOverviewUiState.Loading)
        private set

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            uiState = PortfolioOverviewUiState.Loading
            uiState = runCatching {
                val overview = loadOverview()
                if (overview.portfolio.holdings.isEmpty()) {
                    PortfolioOverviewUiState.Empty
                } else {
                    PortfolioOverviewUiState.Content(overview)
                }
            }.getOrElse { PortfolioOverviewUiState.Error }
        }
    }
}
