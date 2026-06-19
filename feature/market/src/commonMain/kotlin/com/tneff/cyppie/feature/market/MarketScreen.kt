package com.tneff.cyppie.feature.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.market.SpotPrice
import com.tneff.cyppie.feature.market.generated.resources.Res
import com.tneff.cyppie.feature.market.generated.resources.mkt_load_error
import com.tneff.cyppie.feature.market.generated.resources.mkt_retry
import org.jetbrains.compose.resources.stringResource

/** Range chip labels (scaffold — pre-i18n; mkt_* keys land when the copy is finalized). */
private fun MarketRange.label(): String = when (this) {
    MarketRange.DAY -> "1D"
    MarketRange.WEEK -> "1W"
    MarketRange.MONTH -> "1M"
    MarketRange.YEAR -> "1Y"
}

/**
 * Market detail (PRD-04 scaffold): asset header + price chart ([MarketChart] web-seam) + range selector
 * over [MarketViewModel]. Adaptive width like the portfolio screen; DI/nav from the app shell (takes the
 * VM + an [onBack] callback → web-safe). States: loading / content / error-retry (FR-4). Copy/i18n
 * (mkt_*), price/Δ header, and the live `:market` wiring follow the `:market` merge (ADR-0025).
 */
@Composable
fun MarketScreen(
    viewModel: MarketViewModel,
    assetTitle: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = assetTitle, onBack = onBack)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = spacing.xl).testTag("mkt_screen"),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                when (val state = viewModel.uiState) {
                    is MarketUiState.Loading ->
                        Box(Modifier.fillMaxWidth().padding(top = spacing.xl), contentAlignment = Alignment.Center) {
                            ProgressRing(diameter = 32.dp)
                        }
                    is MarketUiState.Content -> {
                        PriceHeader(state.spot)
                        MarketChart(points = state.points, modifier = Modifier.padding(top = spacing.md))
                        RangeSelector(viewModel)
                    }
                    is MarketUiState.Error ->
                        Column(Modifier.fillMaxWidth().padding(top = spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(Res.string.mkt_load_error), style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant)
                            CryptasaButton(text = stringResource(Res.string.mkt_retry), onClick = viewModel::retry, modifier = Modifier.padding(top = spacing.md).testTag("mkt_retry"))
                        }
                }
            }
        }
    }
}

/** Price/Δ header (PRD-04): current spot + 24h change (success/danger). Numeric → LTR island (RTL-safe).
 *  Values are decimal Strings from `:market` (FR-6 — no float). Pre-i18n; mkt_ copy lands later. */
@Composable
private fun PriceHeader(spot: SpotPrice?) {
    if (spot == null) return
    val colors = CryptasaTheme.colors
    Column(modifier = Modifier.fillMaxWidth().testTag("mkt_price_header")) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = "${spot.priceDecimal} ${spot.vs.uppercase()}",
                style = CryptasaTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            spot.change24hPct?.let { pct ->
                val negative = pct.trim().startsWith("-")
                val sign = if (!negative && !pct.trim().startsWith("+")) "+" else ""
                Text(
                    text = "$sign$pct%",
                    style = CryptasaTheme.typography.body,
                    color = if (negative) colors.danger else colors.success,
                    modifier = Modifier.testTag("mkt_change_24h"),
                )
            }
        }
    }
}

@Composable
private fun RangeSelector(viewModel: MarketViewModel) {
    SegmentedControl(
        options = MarketRange.entries.toList(),
        selected = viewModel.range,
        onSelect = viewModel::selectRange,
        label = { it.label() },
        optionTestTag = { "mkt_range_${it.name.lowercase()}" },
    )
}
