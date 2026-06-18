package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-28 AK — ONB-6 ShowSeed on Desktop (`runComposeUiTest`): the phrase is **covered until tapped**,
 * "continue" is gated on the written-it-down acknowledgement (which only appears after reveal), and a
 * **generation failure shows a blocking dialog** with no continue. Synthetic words only (§5.3).
 */
@OptIn(ExperimentalTestApi::class)
class ShowSeedScreenDesktopTest {

    private val words = List(12) { "abandon" }

    private fun runScreen(
        generationFailed: Boolean = false,
        onContinue: () -> Unit = {},
        onGenerate: () -> Unit = {},
        onRetry: () -> Unit = {},
        body: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                ShowSeedScreen(
                    words = words,
                    wordCount = 12,
                    generationFailed = generationFailed,
                    onGenerate = onGenerate,
                    onRetry = onRetry,
                    onContinue = onContinue,
                    onBack = {},
                )
            }
        }
        body()
    }

    @Test
    fun phraseIsCoveredUntilTapped() = runScreen {
        // Covered: the reveal overlay is present; tapping it reveals (overlay gone).
        onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).assertIsDisplayed().performClick()
        onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).assertDoesNotExist()
    }

    @Test
    fun continueGatedOnAcknowledgementAfterReveal() {
        var continued = 0
        runScreen(onContinue = { continued++ }) {
            onNodeWithTag(OnboardingTestTags.SHOW_SEED_CONTINUE).assertIsNotEnabled()
            onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).performClick() // reveal → ack checkbox appears
            onNode(isToggleable()).performClick()                            // "I've written it down"
            onNodeWithTag(OnboardingTestTags.SHOW_SEED_CONTINUE).assertIsEnabled().performClick()
        }
        assertEquals(1, continued)
    }

    @Test
    fun generationFailureShowsBlockingDialogAndNoContinue() = runScreen(generationFailed = true) {
        onNodeWithTag(OnboardingTestTags.SHOW_SEED_ERROR_DIALOG).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.SHOW_SEED_CONTINUE).assertDoesNotExist()
    }

    @Test
    fun generatesOnEntry() {
        var generated = 0
        runScreen(onGenerate = { generated++ }) {
            onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).assertIsDisplayed()
        }
        assertEquals(1, generated) // LaunchedEffect triggers CSPRNG generation once
    }
}
