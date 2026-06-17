package com.tneff.cyppie.feature.onboarding

import androidx.navigation3.runtime.NavKey

/**
 * Navigation keys for the onboarding flow (ADR-0006, Nav3). The back stack is UI-owned state
 * (a `SnapshotStateList<OnboardingNavKey>`); [com.tneff.cyppie.feature.onboarding.OnboardingRoot]
 * renders each key via the Nav3 `entryProvider`.
 *
 * Key names follow the testTag/i18n scheme (`onb_<screen>`). The flow branches after [ChoosePath]
 * into the create path (…→ [ShowSeed] → [ConfirmBackup] →…) or the import path (…→ [ImportSeed] →…),
 * both converging on [WalletSetup] → [Biometrics] (see SEQUENCE.md branch logic).
 *
 * NOTE: process-death persistence of the back stack (serializable keys via `rememberNavBackStack`)
 * is deferred until the serialization stack is decided (ADR-0010, open). Until then the back stack
 * survives configuration changes via `rememberSaveable`-backed state but not process death.
 */
sealed interface OnboardingNavKey : NavKey {
    data object Welcome : OnboardingNavKey
    data object ChoosePath : OnboardingNavKey
    data object SetPassword : OnboardingNavKey
    data object ConfirmPassword : OnboardingNavKey
    data object ShowSeed : OnboardingNavKey
    data object ConfirmBackup : OnboardingNavKey
    data object ImportSeed : OnboardingNavKey
    data object WalletSetup : OnboardingNavKey
    data object Biometrics : OnboardingNavKey
}
