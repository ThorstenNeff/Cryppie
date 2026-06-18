package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingScaffold
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_cta
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_import_link
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_title
import org.jetbrains.compose.resources.stringResource

/**
 * Foundation placeholder for the welcome screen so the app is runnable end-to-end. The real ONB-1
 * screen lands in KAN-5. All user-facing text comes from `composeResources` (no raw strings, §5.5);
 * the two actions carry their contract `testTag`s ([OnboardingTestTags], KAN-10).
 */
@Composable
fun WelcomePlaceholderScreen(
    onStart: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        title = stringResource(Res.string.onb_welcome_title),
        modifier = modifier,
        primaryBar = {
            Column(verticalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.sm)) {
                CryptasaButton(
                    text = stringResource(Res.string.onb_welcome_cta),
                    onClick = onStart,
                    modifier = Modifier.testTag(OnboardingTestTags.WELCOME_START),
                )
                CryptasaButton(
                    text = stringResource(Res.string.onb_welcome_import_link),
                    onClick = onImport,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.testTag(OnboardingTestTags.WELCOME_IMPORT),
                )
            }
        },
    ) {
        Text(
            text = stringResource(Res.string.onb_welcome_body),
            style = CryptasaTheme.typography.body,
            color = CryptasaTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * Generic placeholder for the not-yet-built onboarding screens (2–9). These carry **no** contract
 * `testTag`s on purpose — their `onb_<screen>_<element>` IDs ([OnboardingTestTags]) and final copy
 * (string resources) are wired when each screen is built in its ONB-* ticket (KAN-5+). The [title]
 * passed by the navigation host is a transient scaffold label; the back action uses the shared
 * [Res.string.cd_back] resource.
 */
@Composable
fun OnboardingPlaceholderScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        title = title,
        modifier = modifier,
        primaryBar = onBack?.let {
            {
                CryptasaButton(
                    text = stringResource(Res.string.cd_back),
                    onClick = it,
                    style = CryptasaButtonStyle.Secondary,
                )
            }
        },
        content = {},
    )
}
