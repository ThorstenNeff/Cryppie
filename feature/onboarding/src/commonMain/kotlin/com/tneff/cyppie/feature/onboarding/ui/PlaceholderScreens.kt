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

/**
 * Foundation placeholder for the welcome screen so the app is runnable end-to-end. The real ONB-1
 * screen lands in KAN-5 and its copy moves to string resources in KAN-35; the inline strings here
 * are intentional throwaway scaffolding. The two actions already carry their contract `testTag`s
 * ([OnboardingTestTags.WELCOME_START] / [OnboardingTestTags.WELCOME_IMPORT], KAN-10).
 */
@Composable
fun WelcomePlaceholderScreen(
    onStart: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        title = "Cyppie",
        modifier = modifier,
        primaryBar = {
            Column(verticalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.sm)) {
                CryptasaButton(
                    text = "Neue Wallet erstellen",
                    onClick = onStart,
                    modifier = Modifier.testTag(OnboardingTestTags.WELCOME_START),
                )
                CryptasaButton(
                    text = "Wallet importieren",
                    onClick = onImport,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.testTag(OnboardingTestTags.WELCOME_IMPORT),
                )
            }
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
 * Generic placeholder for the not-yet-built onboarding screens (2–9). These carry **no** contract
 * `testTag`s on purpose — their `onb_<screen>_<element>` IDs ([OnboardingTestTags]) are wired to the
 * real interactive elements when each screen is built in its ONB-* ticket (KAN-5+). The back action
 * is scaffold-only navigation.
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
                    text = "Zurück",
                    onClick = it,
                    style = CryptasaButtonStyle.Secondary,
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
