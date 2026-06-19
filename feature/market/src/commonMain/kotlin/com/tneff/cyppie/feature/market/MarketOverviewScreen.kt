package com.tneff.cyppie.feature.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.market.MarketAsset
import com.tneff.cyppie.feature.market.generated.resources.Res
import com.tneff.cyppie.feature.market.generated.resources.mkt_load_error
import com.tneff.cyppie.feature.market.generated.resources.mkt_overview_title
import com.tneff.cyppie.feature.market.generated.resources.mkt_retry
import com.tneff.cyppie.feature.market.generated.resources.mkt_watchlist_empty
import org.jetbrains.compose.resources.stringResource

/**
 * MD-3 market overview / watchlist (SPEC_MD_overview) — header + a list of watched assets (icon · symbol ·
 * mini-sparkline · price LTR · 24h change icon+text), tap → MD-1. States: Loading (row skeletons) / Error
 * (banner+retry) / Content (empty hint or rows; stale badge when degraded). **No `≈`** (canonical market
 * data). Adaptive: single column centred (max-width); DI/nav from the app shell → web-safe.
 */
@Composable
fun MarketOverviewScreen(
    viewModel: MarketOverviewViewModel,
    onAssetClick: (MarketAsset) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.mkt_overview_title), onBack = onBack)
            when (val state = viewModel.uiState) {
                is MarketOverviewUiState.Loading ->
                    Column(Modifier.fillMaxWidth().padding(spacing.xl).testTag("mkt_loading"), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                        repeat(7) { WatchRowSkeleton() }
                    }
                is MarketOverviewUiState.Error ->
                    CryptasaBanner(
                        title = stringResource(Res.string.mkt_load_error),
                        tone = CryptasaBannerTone.Danger,
                        actionText = stringResource(Res.string.mkt_retry),
                        onActionClick = viewModel::retry,
                        modifier = Modifier.padding(spacing.xl).testTag("mkt_load_error"),
                    )
                is MarketOverviewUiState.Content ->
                    if (state.rows.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(spacing.xl), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.mkt_watchlist_empty), style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant, modifier = Modifier.testTag("mkt_watchlist_empty"))
                        }
                    } else {
                        FreshnessLabel(state.freshness, Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs))
                        LazyColumn(Modifier.fillMaxWidth().testTag("mkt_overview_list")) {
                            itemsIndexed(state.rows) { index, row ->
                                WatchRow(row, onClick = { onAssetClick(row.asset) })
                                if (index < state.rows.lastIndex) HorizontalDivider(color = colors.outline)
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun WatchRow(row: MarketOverviewRow, onClick: () -> Unit) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .padding(horizontal = spacing.xl, vertical = spacing.md)
            .semantics(mergeDescendants = true) {}
            .testTag("mkt_watch_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceVariant)) // asset icon placeholder
        Text(row.label, style = CryptasaTheme.typography.body, color = colors.onSurface, modifier = Modifier.weight(1f))
        MiniSparkline(row.sparkline, Modifier.width(64.dp).heightIn(min = 24.dp, max = 24.dp).testTag("mkt_sparkline"))
        Column(horizontalAlignment = Alignment.End) {
            LtrText(
                text = row.spot?.let { "${it.priceDecimal} ${it.vs.uppercase()}" } ?: "—",
                style = CryptasaTheme.typography.body,
                color = colors.onSurface,
                modifier = Modifier.testTag("mkt_price"),
            )
            ChangeBadge(row.spot?.change24hPct, style = CryptasaTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WatchRowSkeleton() {
    val spacing = CryptasaTheme.spacing
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        SkeletonBox(Modifier.size(32.dp).clip(CircleShape))
        SkeletonBox(Modifier.weight(1f).heightIn(min = 16.dp, max = 16.dp))
        SkeletonBox(Modifier.width(64.dp).heightIn(min = 24.dp, max = 24.dp))
    }
}
