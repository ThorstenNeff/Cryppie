package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel

/** Which onboarding branch the user picked on [OnboardingNavKey.ChoosePath]. */
enum class OnboardingPath { Create, Import }

/** Slots held for the seed phrase (BIP-39 max 24 words); the import screen shows the first 12|24. */
const val MAX_SEED_WORDS: Int = 24

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

    /** Confirmation input (ONB-4), in-memory only — same handling/lifetime as [password]. */
    var confirmPassword: String by mutableStateOf("")
        private set

    /** Seed import (ONB-5): 12 or 24. */
    var seedWordCount: Int by mutableStateOf(12)
        private set

    /**
     * Seed words being entered/imported (ONB-5), in-memory only — never logged/persisted. Fixed
     * [MAX_SEED_WORDS] slots so toggling 12↔24 preserves typed words; the screen uses the first
     * [seedWordCount]. Zeroization is the secure-storage work (Screen 8, ADR-0009).
     */
    val seedWords: SnapshotStateList<String> =
        mutableStateListOf<String>().also { list -> repeat(MAX_SEED_WORDS) { list.add("") } }

    /** True if secure seed generation failed (ONB-6) — UI must block with no insecure fallback. */
    var seedGenerationFailed: Boolean by mutableStateOf(false)
        private set
    private var seedGenerated = false

    fun choosePath(path: OnboardingPath) {
        uiState = uiState.copy(path = path)
    }

    /**
     * Generates a fresh BIP-39 phrase once for the create flow (ONB-6), via the `:wallet` CSPRNG
     * (through [MnemonicSupport]). On failure sets [seedGenerationFailed] — never falls back to
     * insecure entropy. Idempotent within the flow so the seed stays identical across navigation.
     */
    fun generateSeed(wordCount: Int = 12) {
        if (seedGenerated) return
        runCatching { MnemonicSupport.generate(wordCount) }
            .onSuccess { generated ->
                seedWordCount = wordCount
                for (i in seedWords.indices) seedWords[i] = generated.getOrElse(i) { "" }
                seedGenerated = true
                seedGenerationFailed = false
            }
            .onFailure { seedGenerationFailed = true }
    }

    /** Re-attempt generation after a failure (ONB-6 error dialog). */
    fun retrySeedGeneration(wordCount: Int = 12) {
        seedGenerated = false
        seedGenerationFailed = false
        generateSeed(wordCount)
    }

    fun updateSeedWordCount(count: Int) {
        if (count == 12 || count == 24) seedWordCount = count
    }

    fun setSeedWord(index: Int, word: String) {
        if (index in seedWords.indices) seedWords[index] = word
    }

    fun updatePassword(value: String) {
        password = value
    }

    fun updateConfirmPassword(value: String) {
        confirmPassword = value
    }

    fun reset() {
        uiState = OnboardingUiState()
        password = ""
        confirmPassword = ""
        seedWordCount = 12
        for (i in seedWords.indices) seedWords[i] = ""
        seedGenerated = false
        seedGenerationFailed = false
    }
}
