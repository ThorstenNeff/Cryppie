package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaDialog
import com.tneff.cyppie.designsystem.foundation.clickableIcon
import com.tneff.cyppie.designsystem.theme.CryptasaBrandGradient
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.common_close
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_cta
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_err_start_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_err_start_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_import_link
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_tagline
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_title
import androidx.window.core.layout.WindowSizeClass
import org.jetbrains.compose.resources.stringResource

/** Brand wordmark shown in the hero — the product name (PO decision KAN-5), non-localized. */
private const val BRAND_WORDMARK = "Cyppie"

/** Welcome screen state (SPEC_ONBOARDING_SCREEN1 §KMP). */
enum class WelcomeState { Content, StartError }

/**
 * ONB-1 — Welcome (SPEC_ONBOARDING_SCREEN1). Brand hero (fixed green→blue [CryptasaBrandGradient]
 * with a black 0→25 % scrim so the white hero text keeps WCAG AA on the light-green end) over a
 * `surface` bottom sheet (rounded top, `radius/2xl`) carrying headline/body and the two actions.
 *
 * All copy comes from `composeResources` (no raw text, §5.5); colours/spacing/radius are tokens
 * ([CryptasaTheme], no raw values, §5.2). Actions carry their contract testTags
 * ([OnboardingTestTags]). On [WelcomeState.StartError] a blocking, non-dismissable [CryptasaDialog]
 * covers the screen. Content is capped to 480 dp wide so it stays centred on large windows (ADR-0012).
 */
@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onImport: () -> Unit,
    state: WelcomeState = WelcomeState.Content,
    onCloseError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val radius = CryptasaTheme.radius
    val typography = CryptasaTheme.typography

    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    // KAN-94: short height (landscape phone) or Medium/Expanded width → one centred, scrollable,
    // ≤480-wide column (no body/actions overlap, width-clamped). Tall portrait keeps the hero/sheet
    // design. Both share the hero + actions; both scroll the body so it never collides with the CTA.
    val centeredLayout = !windowSize.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ||
        windowSize.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    Box(modifier = modifier.fillMaxSize().background(colors.surface)) {
        if (centeredLayout) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.xl, vertical = spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    WelcomeHero(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 280.dp)
                            .clip(RoundedCornerShape(radius.xxl)),
                    )
                    WelcomeText()
                    WelcomeActions(onStart = onStart, onImport = onImport, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                WelcomeHero(modifier = Modifier.fillMaxWidth().weight(0.46f).heightIn(max = 320.dp))
                // Bottom sheet — surface with rounded top, slightly overlapping the hero.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.54f)
                        .offset(y = (-24).dp)
                        .clip(RoundedCornerShape(topStart = radius.xxl, topEnd = radius.xxl))
                        .background(colors.surface)
                        .padding(horizontal = spacing.xl, vertical = spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        WelcomeText()
                    }
                    WelcomeActions(
                        onStart = onStart,
                        onImport = onImport,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    )
                }
            }
        }

        if (state == WelcomeState.StartError) {
            // Blocking, non-dismissable: single "close" action, no dismiss handler (SPEC §Zustände).
            CryptasaDialog(
                title = stringResource(Res.string.onb_welcome_err_start_title),
                body = stringResource(Res.string.onb_welcome_err_start_body),
                confirmText = stringResource(Res.string.common_close),
                onConfirm = onCloseError,
                modifier = Modifier.testTag(OnboardingTestTags.WELCOME_START_ERROR_DIALOG),
            )
        }
    }
}

/** Brand hero: green→blue gradient + a11y dark mid-band scrim (WCAG AA) + wordmark/tagline. */
@Composable
private fun WelcomeHero(modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val typography = CryptasaTheme.typography
    Box(
        modifier = modifier.background(Brush.linearGradient(CryptasaBrandGradient)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.30f to Color.Black.copy(alpha = 0.66f),
                        0.70f to Color.Black.copy(alpha = 0.66f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.padding(horizontal = spacing.xl),
        ) {
            Text(
                text = BRAND_WORDMARK,
                style = typography.titleLarge,
                color = colors.onPrimary,
                modifier = Modifier.semantics { contentDescription = BRAND_WORDMARK },
            )
            Text(
                text = stringResource(Res.string.onb_welcome_tagline),
                style = typography.body,
                color = colors.onPrimary,
            )
        }
    }
}

/** Headline + body copy. */
@Composable
private fun WelcomeText() {
    val colors = CryptasaTheme.colors
    val typography = CryptasaTheme.typography
    Text(
        text = stringResource(Res.string.onb_welcome_title),
        style = typography.titleLarge,
        color = colors.onSurface,
    )
    Text(
        text = stringResource(Res.string.onb_welcome_body),
        style = typography.body,
        color = colors.onSurfaceVariant,
    )
}

/** Primary "get started" CTA + import text-link. */
@Composable
private fun WelcomeActions(onStart: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val typography = CryptasaTheme.typography
    val spacing = CryptasaTheme.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CryptasaButton(
            text = stringResource(Res.string.onb_welcome_cta),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.WELCOME_START),
        )
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickableIcon(onClick = onImport)
                .testTag(OnboardingTestTags.WELCOME_IMPORT),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.onb_welcome_import_link),
                style = typography.label,
                color = colors.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
