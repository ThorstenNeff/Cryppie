package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.feature.onboarding.OnboardingScaffold
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import org.jetbrains.compose.resources.stringResource

/**
 * Generic placeholder for the not-yet-built onboarding screens (2–9). These carry **no** contract
 * `testTag`s on purpose — their `onb_<screen>_<element>` IDs ([OnboardingTestTags]) and final copy
 * (string resources) are wired when each screen is built in its ONB-* ticket (KAN-11+). The [title]
 * passed by the navigation host is a transient scaffold label; the back action uses the shared
 * [Res.string.cd_back] resource. ONB-1 (welcome) is now a real screen — see [WelcomeScreen].
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
