package com.tneff.cyppie.feature.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.market.SpotPrice
import com.tneff.cyppie.feature.market.generated.resources.Res
import com.tneff.cyppie.feature.market.generated.resources.mkt_empty
import com.tneff.cyppie.feature.market.generated.resources.mkt_load_error
import com.tneff.cyppie.feature.market.generated.resources.mkt_marketcap
import com.tneff.cyppie.feature.market.generated.resources.mkt_open_portfolio
import com.tneff.cyppie.feature.market.generated.resources.mkt_retry
import com.tneff.cyppie.feature.market.generated.resources.mkt_section_market
import com.tneff.cyppie.feature.market.generated.resources.mkt_supply
import com.tneff.cyppie.feature.market.generated.resources.mkt_volume_24h
import org.jetbrains.compose.resources.stringResource

private val TWO_COLUMN_MIN_WIDTH = 600.dp

private fun MarketRange.label(): String = when (this) { // 1D/1W/1M/1Y universal, not translated (SPEC §3)
    MarketRange.DAY -> "1D"; MarketRange.WEEK -> "1W"; MarketRange.MONTH -> "1M"; MarketRange.YEAR -> "1Y"
}

/**
 * MD-1 token/asset detail (SPEC_MD_detail) — header + price block (spot LTR + 24h change icon+text +
 * freshness) + chart container (range tabs + the ADR-0020 Compose-Canvas candlestick) + market stats +
 * optional "view in portfolio". States: Loading (skeleton) / Error (banner+retry) / Content (with empty +
 * stale handled). **No `≈` marker** — market data is canonical (distinct from PF-5). Adaptive: Compact
 * vertical, Medium+ 2-column (stats | chart). DI/nav from the app shell → web-safe.
 */
@Composable
fun MarketScreen(
    viewModel: MarketViewModel,
    assetTitle: String,
    onBack: () -> Unit = {},
    onViewInPortfolio: (() -> Unit)? = null, // non-null only when the asset is held (SPEC §5 → PF-4)
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = assetTitle, onBack = onBack)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.xl).testTag("mkt_screen"),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                when (val state = viewModel.uiState) {
                    is MarketUiState.Loading -> LoadingSkeleton()
                    is MarketUiState.Error ->
                        CryptasaBanner(
                            title = stringResource(Res.string.mkt_load_error),
                            tone = CryptasaBannerTone.Danger,
                            actionText = stringResource(Res.string.mkt_retry),
                            onActionClick = viewModel::retry,
                            modifier = Modifier.padding(top = spacing.md).testTag("mkt_load_error"),
                        )
                    is MarketUiState.Content -> ContentBody(state, viewModel, onViewInPortfolio)
                }
            }
        }
    }
}

@Composable
private fun ContentBody(state: MarketUiState.Content, viewModel: MarketViewModel, onViewInPortfolio: (() -> Unit)?) {
    val spacing = CryptasaTheme.spacing
    PriceBlock(state.spot, state.freshness)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumn = maxWidth >= TWO_COLUMN_MIN_WIDTH
        if (twoColumn) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
                Box(Modifier.weight(1f)) { ChartContainer(state, viewModel) }
                Column(Modifier.weight(1f)) { MarketStats(state.metrics) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                ChartContainer(state, viewModel)
                MarketStats(state.metrics)
            }
        }
    }
    if (onViewInPortfolio != null) {
        CryptasaButton(
            text = stringResource(Res.string.mkt_open_portfolio),
            onClick = onViewInPortfolio,
            style = CryptasaButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().padding(top = spacing.md).testTag("mkt_open_portfolio"),
        )
    }
}

@Composable
private fun PriceBlock(spot: SpotPrice?, freshness: Freshness?) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Column(Modifier.fillMaxWidth().padding(top = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        LtrText(
            text = if (spot != null) "${spot.priceDecimal} ${spot.vs.uppercase()}" else "—",
            style = CryptasaTheme.typography.titleLarge,
            color = colors.onSurface,
            modifier = Modifier.testTag("mkt_price"),
        )
        ChangeBadge(spot?.change24hPct)
        FreshnessLabel(freshness)
    }
}

@Composable
private fun ChartContainer(state: MarketUiState.Content, viewModel: MarketViewModel) {
    val spacing = CryptasaTheme.spacing
    val colors = CryptasaTheme.colors
    Column(Modifier.fillMaxWidth().testTag("mkt_chart_container"), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (state.candles.isEmpty()) {
            // FR-4: loaded but no candle data → neutral empty hint (no chart, no crash).
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.mkt_empty), style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant)
            }
        } else {
            MarketChart(candles = state.candles, modifier = Modifier.testTag("mkt_chart"))
        }
        SegmentedControl(
            options = MarketRange.entries.toList(),
            selected = viewModel.range,
            onSelect = viewModel::selectRange,
            label = { it.label() },
            optionTestTag = { "mkt_range_${it.name.lowercase()}" },
        )
    }
}

@Composable
private fun MarketStats(metrics: MarketMetrics?) {
    val colors = CryptasaTheme.colors
    Column(Modifier.fillMaxWidth().testTag("mkt_section_market")) {
        Text(stringResource(Res.string.mkt_section_market), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
        StatRow(stringResource(Res.string.mkt_marketcap), metrics?.marketCap, "mkt_marketcap")
        StatRow(stringResource(Res.string.mkt_volume_24h), metrics?.volume24h, "mkt_volume_24h")
        StatRow(stringResource(Res.string.mkt_supply), metrics?.circulatingSupply, "mkt_supply")
    }
}

@Composable
private fun LoadingSkeleton() {
    val spacing = CryptasaTheme.spacing
    Column(Modifier.fillMaxWidth().padding(top = spacing.lg).testTag("mkt_loading"), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(36.dp)) // price
        SkeletonBox(Modifier.fillMaxWidth(0.3f).height(18.dp)) // change
        SkeletonBox(Modifier.fillMaxWidth().height(220.dp)) // chart-first (NFR-1)
        repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(20.dp)) } // stat rows
    }
}
