package com.tneff.cyppie.feature.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.window.core.layout.WindowSizeClass
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.NumberFormatProfile
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.portfolio.generated.resources.Res
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_allocation
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_approximate
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_approximate_caveat
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_change_24h
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_empty_body
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_empty_title
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_error_title
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_loading
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_pnl
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_retry
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_title
import com.tneff.cyppie.feature.portfolio.generated.resources.pf_total_value
import com.tneff.cyppie.portfolio.AllocationSlice
import com.tneff.cyppie.portfolio.Metric
import com.tneff.cyppie.market.Money
import org.jetbrains.compose.resources.stringResource

/**
 * PF-1 Portfolio Overview (PRD-03). Renders the headline metrics (total value · 24h change · P&L) and
 * the asset allocation for [state]. FR-9: any `Approximate` metric is marked with "≈" and a warning
 * caveat banner. Layout is adaptive — a single column on compact, two columns (metrics | allocation)
 * from the Medium width breakpoint (web/tablet, ADR-0012). DI/nav are the app shell's job: the screen
 * is driven by an injected [PortfolioOverviewUiState] + [onRetry] callback, so it stays web-safe.
 */
@Composable
fun PortfolioOverviewScreen(
    state: PortfolioOverviewUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    Column(modifier = modifier.fillMaxSize().testTag(PortfolioTestTags.SCREEN)) {
        CryptasaTopAppBar(title = stringResource(Res.string.pf_title), onBack = onBack)
        when (state) {
            PortfolioOverviewUiState.Loading -> CenteredState {
                CircularProgressIndicator(
                    color = colors.primary,
                    modifier = Modifier.testTag(PortfolioTestTags.LOADING),
                )
                Spacer(Modifier.height(spacing.md))
                Text(
                    text = stringResource(Res.string.pf_loading),
                    style = CryptasaTheme.typography.body,
                    color = colors.onSurfaceVariant,
                )
            }

            PortfolioOverviewUiState.Empty -> CenteredState(tag = PortfolioTestTags.EMPTY) {
                Text(
                    text = stringResource(Res.string.pf_empty_title),
                    style = CryptasaTheme.typography.title,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = stringResource(Res.string.pf_empty_body),
                    style = CryptasaTheme.typography.body,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            PortfolioOverviewUiState.Error -> CenteredState(tag = PortfolioTestTags.ERROR) {
                CryptasaBanner(
                    title = stringResource(Res.string.pf_error_title),
                    tone = CryptasaBannerTone.Danger,
                )
                Spacer(Modifier.height(spacing.md))
                CryptasaButton(
                    text = stringResource(Res.string.pf_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(PortfolioTestTags.RETRY),
                )
            }

            is PortfolioOverviewUiState.Content -> PortfolioContent(state.overview)
        }
    }
}

@Composable
private fun PortfolioContent(overview: PortfolioOverview) {
    val spacing = CryptasaTheme.spacing
    val portfolio = overview.portfolio
    val twoColumn = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    // KAN-116: number/currency separators follow the active locale (float-free).
    val languageTag = Locale.current.toLanguageTag()
    val profile = remember(languageTag) { NumberFormatProfile.forLanguageTag(languageTag) }

    val showCaveat = portfolio.totalValue?.isApproximate == true ||
        portfolio.change24h?.isApproximate == true ||
        overview.pnl?.isApproximate == true

    val metrics: @Composable Modifier.() -> Unit = {
        Column(modifier = this, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (showCaveat) {
                CryptasaBanner(
                    title = stringResource(Res.string.pf_approximate),
                    description = stringResource(Res.string.pf_approximate_caveat),
                    tone = CryptasaBannerTone.Warning,
                    modifier = Modifier.testTag(PortfolioTestTags.APPROXIMATE_BANNER),
                )
            }
            MetricHeadline(
                label = stringResource(Res.string.pf_total_value),
                metric = portfolio.totalValue,
                signed = false,
                tag = PortfolioTestTags.TOTAL_VALUE,
                profile = profile,
                large = true,
            )
            MetricHeadline(
                label = stringResource(Res.string.pf_change_24h),
                metric = portfolio.change24h,
                signed = true,
                tag = PortfolioTestTags.CHANGE_24H,
                profile = profile,
            )
            MetricHeadline(
                label = stringResource(Res.string.pf_pnl),
                metric = overview.pnl,
                signed = true,
                tag = PortfolioTestTags.PNL,
                profile = profile,
            )
        }
    }

    val allocation: @Composable Modifier.() -> Unit = {
        Column(modifier = this, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = stringResource(Res.string.pf_allocation),
                style = CryptasaTheme.typography.titleSmall,
                color = CryptasaTheme.colors.onSurface,
            )
            Column(
                modifier = Modifier.fillMaxWidth().testTag(PortfolioTestTags.ALLOCATION_LIST),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                portfolio.allocation.forEachIndexed { i, slice -> AllocationRow(slice, i, profile) }
            }
        }
    }

    if (twoColumn) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            metrics(Modifier.weight(1f))
            allocation(Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            metrics(Modifier.fillMaxWidth())
            allocation(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MetricHeadline(
    label: String,
    metric: Metric<Money>?,
    signed: Boolean,
    tag: String,
    profile: NumberFormatProfile,
    large: Boolean = false,
) {
    val colors = CryptasaTheme.colors
    val value = metric?.value
    val approximate = metric?.isApproximate == true
    val text = when {
        value == null -> "—"
        signed -> value.formattedSigned(profile)
        else -> value.formatted(profile)
    }
    val prefix = if (approximate && value != null) "≈ " else ""
    val color: Color = when {
        value == null -> colors.onSurfaceVariant
        signed && value.minorUnits > 0L -> colors.success
        signed && value.minorUnits < 0L -> colors.danger
        else -> colors.onSurface
    }

    Column(verticalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.xxs)) {
        Text(text = label, style = CryptasaTheme.typography.label, color = colors.onSurfaceVariant)
        Text(
            text = "$prefix$text",
            style = if (large) CryptasaTheme.typography.titleLarge else CryptasaTheme.typography.title,
            color = color,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun AllocationRow(slice: AllocationSlice, index: Int, profile: NumberFormatProfile) {
    val colors = CryptasaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().testTag(PortfolioTestTags.allocationItem(index)),
        horizontalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = slice.token.symbol,
            style = CryptasaTheme.typography.body,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatBps(slice.fractionBps, profile),
            style = CryptasaTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = slice.value.formatted(profile),
            style = CryptasaTheme.typography.body,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun CenteredState(
    tag: String? = null,
    content: @Composable () -> Unit,
) {
    val base = Modifier.fillMaxSize().padding(CryptasaTheme.spacing.xl)
    Box(
        modifier = if (tag != null) base.testTag(tag) else base,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = { content() },
        )
    }
}
