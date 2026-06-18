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
import com.tneff.cyppie.feature.onboarding.ui.BiometricsScreen
import com.tneff.cyppie.feature.onboarding.ui.ConfirmBackupScreen
import com.tneff.cyppie.feature.onboarding.ui.ConfirmPasswordScreen
import com.tneff.cyppie.feature.onboarding.ui.ImportSeedScreen
import com.tneff.cyppie.feature.onboarding.ui.PathScreen
import com.tneff.cyppie.feature.onboarding.ui.SetPasswordScreen
import com.tneff.cyppie.feature.onboarding.ui.ShowSeedScreen
import com.tneff.cyppie.feature.onboarding.ui.WalletSetupScreen
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
fun OnboardingRoot(
    viewModel: OnboardingViewModel = koinViewModel(),
    onComplete: () -> Unit = {},
) {
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
                        ShowSeedScreen(
                            words = viewModel.seedWords,
                            wordCount = viewModel.seedWordCount,
                            generationFailed = viewModel.seedGenerationFailed,
                            onGenerate = { viewModel.generateSeed() },
                            onRetry = { viewModel.retrySeedGeneration() },
                            onContinue = { goTo(OnboardingNavKey.ConfirmBackup) },
                            onBack = ::back,
                        )
                    }
                    entry<OnboardingNavKey.ConfirmBackup> {
                        ConfirmBackupScreen(
                            positions = viewModel.backupPositions,
                            expectedWords = viewModel.seedWords,
                            entries = viewModel.backupEntries,
                            attempts = viewModel.backupAttempts,
                            onEnsureChallenge = { viewModel.ensureBackupChallenge() },
                            onWordChange = viewModel::setBackupEntry,
                            onConfirm = { goTo(OnboardingNavKey.WalletSetup) },
                            onFailure = { viewModel.recordBackupFailure() },
                            // Re-show the (identical) seed = go back to ShowSeed, the previous entry.
                            onReshowSeed = ::back,
                            onBack = ::back,
                        )
                    }
                    entry<OnboardingNavKey.ImportSeed> {
                        ImportSeedScreen(
                            wordCount = viewModel.seedWordCount,
                            words = viewModel.seedWords,
                            onWordCountChange = viewModel::updateSeedWordCount,
                            onWordChange = viewModel::setSeedWord,
                            onImport = { goTo(OnboardingNavKey.WalletSetup) },
                            onBack = ::back,
                        )
                    }
                    entry<OnboardingNavKey.WalletSetup> {
                        WalletSetupScreen(
                            words = viewModel.seedWords.take(viewModel.seedWordCount),
                            password = viewModel.password,
                            onSuccess = { goTo(OnboardingNavKey.Biometrics) },
                            onSeedPersisted = { viewModel.clearSeedMaterial() },
                            // Cancel after a failure: nothing was persisted — reset the flow to the start.
                            onCancel = {
                                viewModel.reset()
                                backStack.clear()
                                backStack.add(OnboardingNavKey.Welcome)
                            },
                        )
                    }
                    entry<OnboardingNavKey.Biometrics> {
                        BiometricsScreen(
                            password = viewModel.password,
                            // Onboarding complete: zeroize the in-memory secrets, then hand off to the
                            // app shell (→ Home/Unlock; KAN-89). Default no-op keeps standalone callers safe.
                            onFinish = {
                                viewModel.reset()
                                onComplete()
                            },
                            onOpenSettings = {}, // platform settings deep-link = follow-up
                        )
                    }
                },
            )
        }
    }
}
