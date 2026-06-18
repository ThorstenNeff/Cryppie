package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.SelectionCard
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_create_sub
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_create_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_import_sub
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_import_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_offline_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_offline_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_subtitle
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_path_title
import org.jetbrains.compose.resources.stringResource

/**
 * ONB-2 — Choose path (SPEC_ONBOARDING_SCREEN2). Back bar + title/subtitle, an optional offline
 * banner, and two navigation [SelectionCard]s (create / import). Both cards continue to the app
 * password (Screen 3); the chosen [com.tneff.cyppie.feature.onboarding.OnboardingPath] is persisted
 * by the flow controller in [com.tneff.cyppie.feature.onboarding.OnboardingRoot].
 *
 * [isOffline] only drives the banner — creating a wallet works offline and stays enabled (§Zustände).
 * All copy from `composeResources`, all values from tokens; content capped to 480 dp + scrollable
 * (adaptive, Dynamic Type). Cards/banner carry their `onb_path_*` contract testTags.
 */
@Composable
fun PathScreen(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isOffline: Boolean = false,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .testTag(OnboardingTestTags.PATH_SCREEN),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            CryptasaTopAppBar(
                onBack = onBack,
                backContentDescription = stringResource(Res.string.cd_back),
            )
            Text(
                text = stringResource(Res.string.onb_path_title),
                style = CryptasaTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(Res.string.onb_path_subtitle),
                style = CryptasaTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )

            if (isOffline) {
                CryptasaBanner(
                    title = stringResource(Res.string.onb_path_offline_title),
                    description = stringResource(Res.string.onb_path_offline_body),
                    tone = CryptasaBannerTone.Offline,
                    modifier = Modifier.testTag(OnboardingTestTags.PATH_OFFLINE_BANNER),
                )
            }

            SelectionCard(
                title = stringResource(Res.string.onb_path_create_title),
                description = stringResource(Res.string.onb_path_create_sub),
                icon = CryptasaIcons.AddCircle,
                trailingIcon = CryptasaIcons.ChevronRight,
                onClick = onCreate,
                modifier = Modifier.testTag(OnboardingTestTags.PATH_CREATE),
            )
            SelectionCard(
                title = stringResource(Res.string.onb_path_import_title),
                description = stringResource(Res.string.onb_path_import_sub),
                icon = CryptasaIcons.Download,
                trailingIcon = CryptasaIcons.ChevronRight,
                onClick = onImport,
                modifier = Modifier.testTag(OnboardingTestTags.PATH_IMPORT),
            )
        }
    }
}
