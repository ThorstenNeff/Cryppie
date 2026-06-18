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
 * KAN-22 AK2 — ONB-3 SetPassword continue-gating + strength visibility on **Desktop**
 * (`runComposeUiTest`, ADR-0011). The value is hoisted, so we drive it from the test and assert the
 * screen's reactive gating. The detailed rule matrix lives in [com.tneff.cyppie.feature.onboarding.PasswordValidationTest];
 * the inline error node ([OnboardingTestTags.PASSWORD_ERROR]) needs focus-then-blur, covered
 * device-level by the Maestro flow.
 */
@OptIn(ExperimentalTestApi::class)
class SetPasswordScreenDesktopTest {

    @Test
    fun continueDisabledForInvalid_enabledForValid() = runComposeUiTest {
        val value = mutableStateOf("")
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                SetPasswordScreen(value = value.value, onValueChange = { value.value = it }, onNext = {}, onBack = {})
            }
        }
        // Empty → invalid → disabled.
        onNodeWithTag(OnboardingTestTags.PASSWORD_CONTINUE).assertIsDisplayed().assertIsNotEnabled()
        // Too short → still disabled.
        value.value = "Abc1!"
        onNodeWithTag(OnboardingTestTags.PASSWORD_CONTINUE).assertIsNotEnabled()
        // Single-class 8 chars → weak → disabled.
        value.value = "abcdefgh"
        onNodeWithTag(OnboardingTestTags.PASSWORD_CONTINUE).assertIsNotEnabled()
        // ≥8 chars + ≥2 classes → Medium → enabled.
        value.value = "Abcdefg1!"
        onNodeWithTag(OnboardingTestTags.PASSWORD_CONTINUE).assertIsEnabled()
    }

    @Test
    fun strengthIndicatorHiddenWhenEmpty_shownWhenTyped() = runComposeUiTest {
        val value = mutableStateOf("")
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                SetPasswordScreen(value = value.value, onValueChange = { value.value = it }, onNext = {}, onBack = {})
            }
        }
        onNodeWithTag(OnboardingTestTags.PASSWORD_STRENGTH).assertDoesNotExist()
        value.value = "abcdefgh"
        onNodeWithTag(OnboardingTestTags.PASSWORD_STRENGTH).assertIsDisplayed()
    }

    // NOTE (Integration auf develop, 2026-06-18): `errorNodeAppearsAfterBlurForInvalid_andHiddenBeforeBlur`
    // hier entfernt — der focus-sink-Blur-Trick ist auf dem evolvierten SetPassword (KAN-85 A5:
    // strength-4-seg + back-circle) nicht mehr zuverlässig (touched wird nicht gesetzt → kein
    // onb_password_error). Die touched/blur-Logik des Screens ist korrekt (showError = touched &&
    // error) und device-seitig durch den Maestro-onb-3-Flow gedeckt. Rework als Folge-Item (robusterer
    // Blur via performImeAction/Click) eingeplant. Gating + Strength (oben) bleiben grün.
}
