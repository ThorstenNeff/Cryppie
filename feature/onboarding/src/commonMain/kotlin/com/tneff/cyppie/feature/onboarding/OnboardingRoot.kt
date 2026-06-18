package com.tneff.cyppie.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.ConfirmPasswordScreen
import com.tneff.cyppie.feature.onboarding.ui.OnboardingPlaceholderScreen
import com.tneff.cyppie.feature.onboarding.ui.PathScreen
import com.tneff.cyppie.feature.onboarding.ui.SetPasswordScreen
import com.tneff.cyppie.feature.onboarding.ui.WelcomeScreen
import com.tneff.cyppie.feature.onboarding.ui.WelcomeState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Public entry point of the onboarding feature (replaces `AuthRoot` as the app's start, ADR-0004).
 * Hosts the central [CryptasaTheme] and the Nav3 back stack (ADR-0006). Activates
 * `testTagsAsResourceId` once at the root so every `testTag` becomes a resource-id for Maestro
 * (§5.1 / KAN-10).
 *
 * The Koin-provided [OnboardingViewModel] holds the cross-screen flow state (Create/Import path),
 * set on ONB-2 and consumed by the later branch (ADR-0007).
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
                        val welcomeState = remember {
                            if (verifyAppIntegrity()) WelcomeState.Content else WelcomeState.StartError
                        }
                        WelcomeScreen(
                            state = welcomeState,
                            onStart = { goTo(OnboardingNavKey.ChoosePath) },
                            // Import-link = shortcut into the import flow: pick the Import path and go
                            // to the mandatory app password (Screen 3) — NOT straight to seed entry
                            // (the password must not be skipped; SPEC_ONBOARDING_SCREEN1 §Verhalten,
                            // same wiring as ONB-2's import card).
                            onImport = {
                                viewModel.choosePath(OnboardingPath.Import)
                                goTo(OnboardingNavKey.SetPassword)
                            },
                            // Corrupted install: blocking dialog stays up; real close/exit is
                            // platform-specific and lands with the integrity check (ADR-0009).
                            onCloseError = {},
                        )
                    }
                    entry<OnboardingNavKey.ChoosePath> {
                        val connectivity = remember { observeConnectivity() }
                        val online by connectivity.collectAsState(initial = true)
                        PathScreen(
                            isOffline = !online,
                            onBack = ::back,
                            onCreate = {
                                viewModel.choosePath(OnboardingPath.Create)
                                goTo(OnboardingNavKey.SetPassword)
                            },
                            onImport = {
                                viewModel.choosePath(OnboardingPath.Import)
                                goTo(OnboardingNavKey.SetPassword)
                            },
                        )
                    }
                    entry<OnboardingNavKey.SetPassword> {
                        SetPasswordScreen(
                            value = viewModel.password,
                            onValueChange = viewModel::updatePassword,
                            onNext = { goTo(OnboardingNavKey.ConfirmPassword) },
                            onBack = ::back,
                        )
                    }
                    entry<OnboardingNavKey.ConfirmPassword> {
                        ConfirmPasswordScreen(
                            value = viewModel.confirmPassword,
                            password = viewModel.password,
                            onValueChange = viewModel::updateConfirmPassword,
                            onNext = {
                                // Branch by the path chosen on ONB-2: import → seed entry (Screen 5),
                                // create → seed display (Screen 6).
                                goTo(
                                    if (viewModel.uiState.path == OnboardingPath.Import) {
                                        OnboardingNavKey.ImportSeed
                                    } else {
                                        OnboardingNavKey.ShowSeed
                                    },
                                )
                            },
                            onBack = ::back,
                        )
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
