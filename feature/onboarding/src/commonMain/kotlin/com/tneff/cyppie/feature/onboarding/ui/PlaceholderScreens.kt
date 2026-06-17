package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingScaffold

/**
 * Foundation placeholder for the welcome screen so the app is runnable end-to-end. The real ONB-1
 * screen lands in KAN-5 and its copy moves to string resources in KAN-35; the inline strings here
 * are intentional throwaway scaffolding.
 */
@Composable
fun WelcomePlaceholderScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        title = "Cyppie",
        modifier = modifier.testTag("onb_welcome_screen"),
        primaryBar = {
            CryptasaButton(
                text = "Los geht's",
                onClick = onStart,
                modifier = Modifier.testTag("onb_welcome_start"),
            )
        },
    ) {
        Text(
            text = "Onboarding-Foundations stehen (Theme, Komponenten, adaptiver Scaffold, Nav3, Koin). " +
                "Die echten Screens folgen ab KAN-5.",
            style = CryptasaTheme.typography.body,
            color = CryptasaTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * Generic placeholder for the not-yet-built onboarding screens. Each carries a `testTag` so Maestro
 * flows (KAN-10) can already target the route while the real screen is implemented in its ONB-* ticket.
 */
@Composable
fun OnboardingPlaceholderScreen(
    title: String,
    testTag: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        title = title,
        modifier = modifier.testTag(testTag),
        primaryBar = onBack?.let {
            {
                CryptasaButton(
                    text = "Zurück",
                    onClick = it,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.testTag("${testTag}_back"),
                )
            }
        },
    ) {
        Text(
            text = "Platzhalter — wird in der jeweiligen ONB-Story implementiert.",
            style = CryptasaTheme.typography.body,
            color = CryptasaTheme.colors.onSurfaceVariant,
        )
    }
}
