package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaDialog
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.BiometricAvailability
import com.tneff.cyppie.feature.onboarding.BiometricEnableResult
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.rememberBiometricSupport
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.common_continue
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_cta
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_later
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_link_action
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_link_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_link_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_open_settings
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_perm_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_perm_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_reg_failed
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_skip_later
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_unavail_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_unavail_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private enum class BioPhase { Default, Unavailable, RegistrationFailed, PermissionRevoked, LinkFailed }

/**
 * ONB-9 — Enable biometric unlock (SPEC_ONBOARDING_SCREEN9), the only optional step. The app password
 * (ONB-8) is always the fallback, so every path can finish onboarding. "Enable now" runs the native
 * prompt + links the wallet via the [rememberBiometricSupport] seam; "later"/"skip" finishes without
 * it. Covers the five spec states: default, unavailable (informational — no danger styling),
 * registration-failed (inline + retry), permission-revoked (warning banner + open settings),
 * link-failed (dialog → continue without). Copy from resources, tokens, testTags `onb_biometric_*`.
 */
@Composable
fun BiometricsScreen(
    password: String,
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val support = rememberBiometricSupport()
    val scope = rememberCoroutineScope()

    var phase by remember {
        mutableStateOf(
            when (support.availability()) {
                BiometricAvailability.Available -> BioPhase.Default
                BiometricAvailability.PermissionDenied -> BioPhase.PermissionRevoked
                BiometricAvailability.NotEnrolled, BiometricAvailability.NoHardware -> BioPhase.Unavailable
            },
        )
    }
    var busy by remember { mutableStateOf(false) }

    fun attemptEnable() {
        if (busy) return
        busy = true
        scope.launch {
            val result = support.enable(password)
            busy = false
            when (result) {
                BiometricEnableResult.Success -> onFinish()
                BiometricEnableResult.AuthFailed -> phase = BioPhase.RegistrationFailed
                BiometricEnableResult.PermissionDenied -> phase = BioPhase.PermissionRevoked
                BiometricEnableResult.LinkFailed -> phase = BioPhase.LinkFailed
                BiometricEnableResult.Unavailable -> phase = BioPhase.Unavailable
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthInColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            val neutral = phase == BioPhase.Unavailable
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (neutral) colors.surfaceVariant else colors.primarySurface),
            )

            Text(
                text = stringResource(if (neutral) Res.string.onb_bio_unavail_title else Res.string.onb_bio_title),
                style = CryptasaTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                // onb_bio_body has a %1$s for the biometry method name (KAN-101 i18n-Low); the unavail
                // copy has no placeholder. Pass the name only for the substituting string.
                text = if (neutral) stringResource(Res.string.onb_bio_unavail_body)
                else stringResource(Res.string.onb_bio_body, support.biometryTypeName()),
                style = CryptasaTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            when (phase) {
                BioPhase.RegistrationFailed -> Text(
                    text = stringResource(Res.string.onb_bio_reg_failed),
                    style = CryptasaTheme.typography.helper,
                    color = colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag(OnboardingTestTags.BIOMETRIC_REG_ERROR)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                BioPhase.PermissionRevoked -> CryptasaBanner(
                    title = stringResource(Res.string.onb_bio_perm_title),
                    description = stringResource(Res.string.onb_bio_perm_body),
                    tone = CryptasaBannerTone.Warning,
                    actionText = stringResource(Res.string.onb_bio_open_settings),
                    onActionClick = onOpenSettings,
                    modifier = Modifier.testTag(OnboardingTestTags.BIOMETRIC_PERMISSION_BANNER),
                )
                else -> {}
            }
        }

        // Bottom actions.
        Column(
            modifier = Modifier.widthInColumn().align(Alignment.BottomCenter).padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                BioPhase.Unavailable -> CryptasaButton(
                    text = stringResource(Res.string.common_continue),
                    onClick = onFinish,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.BIOMETRIC_UNAVAILABLE),
                )
                else -> {
                    CryptasaButton(
                        text = stringResource(Res.string.onb_bio_cta),
                        onClick = { attemptEnable() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.BIOMETRIC_ENABLE),
                    )
                    CryptasaButton(
                        text = stringResource(
                            if (phase == BioPhase.RegistrationFailed) Res.string.onb_bio_skip_later else Res.string.onb_bio_later,
                        ),
                        onClick = onFinish,
                        enabled = !busy,
                        style = CryptasaButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.BIOMETRIC_SKIP),
                    )
                }
            }
        }
    }

    if (phase == BioPhase.LinkFailed) {
        CryptasaDialog(
            title = stringResource(Res.string.onb_bio_link_title),
            body = stringResource(Res.string.onb_bio_link_body),
            confirmText = stringResource(Res.string.onb_bio_link_action),
            onConfirm = onFinish,
            icon = CryptasaIcons.Error,
            modifier = Modifier.testTag(OnboardingTestTags.BIOMETRIC_LINK_DIALOG),
        )
    }
}

/** Shared 480 dp content cap for this screen's stacked columns (centered by the parent Box). */
private fun Modifier.widthInColumn(): Modifier = this.widthIn(max = 480.dp).fillMaxWidth()
