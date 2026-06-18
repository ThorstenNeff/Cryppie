package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Which onboarding branch the user picked on [OnboardingNavKey.ChoosePath]. */
enum class OnboardingPath { Create, Import }

/** Explicit onboarding flow state (ADR-0002). Extended per screen in the ONB-* tickets. */
data class OnboardingUiState(
    val path: OnboardingPath? = null,
)

/**
 * Flow controller for the onboarding feature (MVVM, ADR-0002; provided via Koin, ADR-0007).
 * Holds the cross-screen flow state; navigation itself is the UI-owned Nav3 back stack in
 * [OnboardingRoot]. Foundation scaffold only — real intents land with the ONB-* screens.
 */
class OnboardingViewModel : ViewModel() {
    var uiState by mutableStateOf(OnboardingUiState())
        private set

    /**
     * The app password while it is being set/confirmed (ONB-3/4). Held in memory only — never
     * logged, never persisted; deliberately kept off [OnboardingUiState] (and its `toString`).
     * At-rest handling (KDF/encryption, zeroization) is the secure-storage work (Screen 8, ADR-0009).
     */
    var password: String by mutableStateOf("")
        private set

    fun choosePath(path: OnboardingPath) {
        uiState = uiState.copy(path = path)
    }

    fun updatePassword(value: String) {
        password = value
    }

    fun reset() {
        uiState = OnboardingUiState()
        password = ""
    }
}
