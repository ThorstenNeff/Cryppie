package com.tneff.cyppie.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/** Adaptive layout bucket derived from the Material 3 Window Size Class (ADR-0012). */
enum class OnboardingWindowClass { Compact, CompactLandscape, Medium, Expanded }

/** Content column never exceeds this width on large screens (ADR-0012). */
private val MaxContentWidth = 480.dp
private val BrandBandWidth = 300.dp

/** Derives the [OnboardingWindowClass] from the current window (ADR-0012 breakpoints). */
@Composable
fun rememberOnboardingWindowClass(): OnboardingWindowClass {
    val wsc: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val expanded = wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) // ≥ 840
    val mediumWidth = wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) // ≥ 600
    val compactHeight = !wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) // < 480
    return when {
        expanded -> OnboardingWindowClass.Expanded
        mediumWidth -> OnboardingWindowClass.Medium
        compactHeight -> OnboardingWindowClass.CompactLandscape
        else -> OnboardingWindowClass.Compact
    }
}

/**
 * Shared adaptive scaffold for every onboarding screen (ADR-0012, HANDOFF §7a). Picks the layout
 * from [windowClass]:
 * - **Compact** (phone portrait): single column, content scrolls, primary action pinned bottom.
 * - **CompactLandscape** (phone landscape, height < 480): narrow title band + scrolling content,
 *   CTA sticky.
 * - **Medium** (tablet/foldable): centred card (max-width 480) on `surface`.
 * - **Expanded** (desktop/large): brand column + centred card (max-width 480).
 *
 * Input screens get `verticalScroll` + `imePadding` so the keyboard never hides the CTA. The
 * `windowClass` is injectable so Robolectric/Compose-UI tests can force a layout (ADR-0013).
 */
@Composable
fun OnboardingScaffold(
    title: String,
    modifier: Modifier = Modifier,
    windowClass: OnboardingWindowClass = rememberOnboardingWindowClass(),
    brand: (@Composable () -> Unit)? = null,
    primaryBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    Surface(modifier = modifier.fillMaxSize(), color = colors.surface, contentColor = colors.onSurface) {
        when (windowClass) {
            OnboardingWindowClass.Compact -> CompactLayout(title, primaryBar, content)
            OnboardingWindowClass.CompactLandscape -> CompactLandscapeLayout(title, primaryBar, content)
            OnboardingWindowClass.Medium -> CenteredCardLayout(title, primaryBar, content)
            OnboardingWindowClass.Expanded -> ExpandedLayout(title, brand, primaryBar, content)
        }
    }
}

@Composable
private fun CompactLayout(
    title: String,
    primaryBar: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = CryptasaTheme.spacing
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(spacing.xl)) {
        TitleAndContent(title, Modifier.weight(1f), content)
        primaryBar?.let { Box(Modifier.padding(top = spacing.md)) { it() } }
    }
}

@Composable
private fun CompactLandscapeLayout(
    title: String,
    primaryBar: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = CryptasaTheme.spacing
    Row(modifier = Modifier.fillMaxSize().imePadding().padding(spacing.lg)) {
        Box(modifier = Modifier.width(BrandBandWidth).fillMaxHeight().padding(end = spacing.lg)) {
            Text(text = title, style = CryptasaTheme.typography.titleLarge, color = CryptasaTheme.colors.onSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
                content = content,
            )
            primaryBar?.let { Box(Modifier.padding(top = spacing.md)) { it() } }
        }
    }
}

@Composable
private fun CenteredCardLayout(
    title: String,
    primaryBar: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = CryptasaTheme.spacing
    Box(modifier = Modifier.fillMaxSize().imePadding().padding(spacing.xl), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
            TitleAndContent(title, Modifier.weight(1f, fill = false), content)
            primaryBar?.let { Box(Modifier.padding(top = spacing.md)) { it() } }
        }
    }
}

@Composable
private fun ExpandedLayout(
    title: String,
    brand: (@Composable () -> Unit)?,
    primaryBar: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = CryptasaTheme.spacing
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(spacing.xxxl),
            contentAlignment = Alignment.Center,
        ) {
            brand?.invoke() ?: Text(
                text = title,
                style = CryptasaTheme.typography.titleLarge,
                color = CryptasaTheme.colors.onSurface,
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().imePadding().padding(spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                TitleAndContent(title, Modifier.weight(1f, fill = false), content)
                primaryBar?.let { Box(Modifier.padding(top = spacing.md)) { it() } }
            }
        }
    }
}

@Composable
private fun TitleAndContent(
    title: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = CryptasaTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = title, style = CryptasaTheme.typography.titleLarge, color = CryptasaTheme.colors.onSurface)
        content()
    }
}
