package com.tneff.cyppie.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.OnboardingPlaceholderScreen
import com.tneff.cyppie.feature.onboarding.ui.WelcomePlaceholderScreen

/**
 * Public entry point of the onboarding feature (replaces `AuthRoot` as the app's start, ADR-0004).
 * Hosts the central [CryptasaTheme] and the Nav3 back stack (ADR-0006). Activates
 * `testTagsAsResourceId` once at the root so every `testTag` becomes a resource-id for Maestro
 * (§5.1 / KAN-10).
 *
 * The Koin-provided [OnboardingViewModel] (flow state, e.g. Create/Import) is injected by the first
 * screen that needs it — wired in with ONB-2 (KAN-11). The module is already registered (ADR-0007).
 */
@Composable
fun OnboardingRoot() {
    CryptasaTheme {
        val backStack: SnapshotStateList<OnboardingNavKey> =
            remember { mutableStateListOf(OnboardingNavKey.Welcome) }

        fun goTo(key: OnboardingNavKey) {
            if (backStack.lastOrNull() != key) backStack.add(key)
        }
        fun back() {
            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        }

        Box(modifier = Modifier.fillMaxSize().enableTestTagsAsResourceId()) {
            NavDisplay(
                backStack = backStack,
                onBack = { back() },
                entryProvider = entryProvider {
                    entry<OnboardingNavKey.Welcome> {
                        WelcomePlaceholderScreen(
                            onStart = { goTo(OnboardingNavKey.ChoosePath) },
                            onImport = { goTo(OnboardingNavKey.ImportSeed) },
                        )
                    }
                    entry<OnboardingNavKey.ChoosePath> {
                        OnboardingPlaceholderScreen("Pfad wählen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.SetPassword> {
                        OnboardingPlaceholderScreen("App-Passwort", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ConfirmPassword> {
                        OnboardingPlaceholderScreen("Passwort bestätigen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ShowSeed> {
                        OnboardingPlaceholderScreen("Seed anzeigen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ConfirmBackup> {
                        OnboardingPlaceholderScreen("Backup bestätigen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ImportSeed> {
                        OnboardingPlaceholderScreen("Seed eingeben", onBack = ::back)
                    }
                    entry<OnboardingNavKey.WalletSetup> {
                        OnboardingPlaceholderScreen("Wallet wird eingerichtet", onBack = ::back)
                    }
                    entry<OnboardingNavKey.Biometrics> {
                        OnboardingPlaceholderScreen("Biometrie aktivieren", onBack = ::back)
                    }
                },
            )
        }
    }
}
