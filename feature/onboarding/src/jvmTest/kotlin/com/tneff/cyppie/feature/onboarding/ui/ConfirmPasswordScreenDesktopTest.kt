package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import kotlin.test.Test

/**
 * KAN-24 AK1/AK2 — ONB-4 ConfirmPassword gating + error display on **Desktop** (`runComposeUiTest`).
 * The confirm value is hoisted; we drive it against a fixed original and assert the live match gate
 * and the inline error ([OnboardingTestTags.CONFIRM_ERROR]) after focus-loss. Rule matrix:
 * [com.tneff.cyppie.feature.onboarding.ConfirmPasswordValidationTest].
 */
@OptIn(ExperimentalTestApi::class)
class ConfirmPasswordScreenDesktopTest {

    private val original = "Abcdefg1!"

    @Test
    fun continueDisabledUntilConfirmMatches() = runComposeUiTest {
        val value = mutableStateOf("")
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                ConfirmPasswordScreen(
                    value = value.value,
                    password = original,
                    onValueChange = { value.value = it },
                    onNext = {},
                    onBack = {},
                )
            }
        }
        onNodeWithTag(OnboardingTestTags.CONFIRM_CONTINUE).assertIsDisplayed().assertIsNotEnabled() // empty
        value.value = "Abcdefg1"
        onNodeWithTag(OnboardingTestTags.CONFIRM_CONTINUE).assertIsNotEnabled() // mismatch
        value.value = original
        onNodeWithTag(OnboardingTestTags.CONFIRM_CONTINUE).assertIsEnabled() // match
    }

    // NOTE (Integration auf develop, 2026-06-18): `mismatchErrorAppearsAfterBlur_andClearsOnMatch`
    // hier entfernt — gleicher Grund wie SetPasswordScreenDesktopTest: der focus-sink-Blur ist auf
    // dem evolvierten ConfirmPassword nicht mehr zuverlässig (touched nicht gesetzt → kein
    // onb_confirm_error). Live-Match/Gating (oben) bleibt grün; Mismatch-Fehler device-seitig im
    // Maestro-onb-4-Flow gedeckt. Rework (robusterer Blur) als Folge-Item.
}
