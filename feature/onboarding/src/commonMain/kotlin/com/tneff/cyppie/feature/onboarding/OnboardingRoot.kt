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
import org.koin.compose.viewmodel.koinViewModel

/**
 * Public entry point of the onboarding feature (replaces `AuthRoot` as the app's start, ADR-0004).
 * Hosts the central [CryptasaTheme], the Koin-provided [OnboardingViewModel] and the Nav3 back stack
 * (ADR-0006). Activates `testTagsAsResourceId` once at the root so every `testTag` becomes a
 * resource-id for Maestro (§5.1 / KAN-10).
 */
@Composable
fun OnboardingRoot(viewModel: OnboardingViewModel = koinViewModel()) {
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
                        WelcomePlaceholderScreen(onStart = { goTo(OnboardingNavKey.ChoosePath) })
                    }
                    entry<OnboardingNavKey.ChoosePath> {
                        OnboardingPlaceholderScreen("Pfad wählen", "onb_choose_path_screen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.SetPassword> {
                        OnboardingPlaceholderScreen("App-Passwort", "onb_set_password_screen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ConfirmPassword> {
                        OnboardingPlaceholderScreen("Passwort bestätigen", "onb_confirm_password_screen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ShowSeed> {
                        OnboardingPlaceholderScreen("Seed anzeigen", "onb_show_seed_screen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ConfirmBackup> {
                        OnboardingPlaceholderScreen("Backup bestätigen", "onb_confirm_backup_screen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.ImportSeed> {
                        OnboardingPlaceholderScreen("Seed eingeben", "onb_import_seed_screen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.WalletSetup> {
                        OnboardingPlaceholderScreen("Wallet wird eingerichtet", "onb_wallet_setup_screen", onBack = ::back)
                    }
                    entry<OnboardingNavKey.Biometrics> {
                        OnboardingPlaceholderScreen("Biometrie aktivieren", "onb_biometrics_screen", onBack = ::back)
                    }
                },
            )
        }
    }
}
