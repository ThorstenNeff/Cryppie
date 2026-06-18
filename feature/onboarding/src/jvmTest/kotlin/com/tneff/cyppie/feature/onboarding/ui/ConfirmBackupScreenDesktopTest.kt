package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-30 AK — ONB-7 ConfirmBackup on Desktop (`runComposeUiTest`): "confirm" is disabled until the 3
 * challenge fields are filled; a wrong word flags **only the affected field** (`onb_backup_error`)
 * and does not confirm; all-correct confirms; and from the 3rd failed attempt a re-show banner
 * offers ONB-6. Synthetic words only — the expected word is never revealed by the UI (§5.3).
 */
@OptIn(ExperimentalTestApi::class)
class ConfirmBackupScreenDesktopTest {

    private val expected = listOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident",
    )
    private val positions = listOf(0, 4, 9) // challenged slots 1..3
    private val correct = mapOf(0 to "abandon", 4 to "above", 9 to "abuse")

    private fun runScreen(
        entries: Map<Int, String>,
        attempts: Int = 0,
        onConfirm: () -> Unit = {},
        onFailure: () -> Unit = {},
        onReshow: () -> Unit = {},
        body: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        var current by mutableStateOf(entries)
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                ConfirmBackupScreen(
                    positions = positions,
                    expectedWords = expected,
                    entries = current,
                    attempts = attempts,
                    onEnsureChallenge = {},
                    onWordChange = { pos, w -> current = current + (pos to w) },
                    onConfirm = onConfirm,
                    onFailure = onFailure,
                    onReshowSeed = onReshow,
                    onBack = {},
                )
            }
        }
        body()
    }

    @Test
    fun confirmDisabledUntilAllFilled() = runScreen(mapOf(0 to "abandon", 4 to "above")) {
        onNodeWithTag(OnboardingTestTags.BACKUP_CONTINUE).assertIsNotEnabled()
    }

    @Test
    fun allCorrectConfirms() {
        var confirmed = 0
        runScreen(correct, onConfirm = { confirmed++ }) {
            onNodeWithTag(OnboardingTestTags.BACKUP_CONTINUE).assertIsEnabled().performClick()
        }
        assertEquals(1, confirmed)
    }

    @Test
    fun wrongWordFlagsAffectedFieldAndBlocksConfirm() {
        var confirmed = 0
        var failed = 0
        runScreen(mapOf(0 to "abandon", 4 to "above", 9 to "zzzzzz"), onConfirm = { confirmed++ }, onFailure = { failed++ }) {
            onNodeWithTag(OnboardingTestTags.BACKUP_CONTINUE).assertIsEnabled().performClick()
            onNodeWithTag(OnboardingTestTags.BACKUP_ERROR).assertIsDisplayed() // only the wrong field flagged
            onNodeWithTag(OnboardingTestTags.BACKUP_CONTINUE).assertIsNotEnabled() // gated until corrected
        }
        assertEquals(0, confirmed)
        assertEquals(1, failed)
    }

    @Test
    fun reshowBannerAppearsFromThirdAttempt() {
        var reshown = 0
        runScreen(emptyMap(), attempts = 3, onReshow = { reshown++ }) {
            onNodeWithText("Show recovery phrase again").assertIsDisplayed().performClick()
        }
        assertEquals(1, reshown)
    }

    @Test
    fun noReshowBannerBeforeThreshold() = runScreen(emptyMap(), attempts = 0) {
        onNodeWithText("Show recovery phrase again").assertDoesNotExist()
    }
}
