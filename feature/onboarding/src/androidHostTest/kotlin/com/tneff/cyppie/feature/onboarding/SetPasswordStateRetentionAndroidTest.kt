package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.SetPasswordScreen
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * KAN-22 AK4 (state retention, ADR-0013) — the **entered password survives a saved-state restore**
 * (config change / rotation / process recreation).
 *
 * The real screen hoists the value to the flow ViewModel; here we model that persistence with a
 * `rememberSaveable` holder and verify it across [StateRestorationTester.emulateSavedInstanceStateRestore].
 * Survival is asserted via the continue gate (enabled ⇔ a valid password is present) — a robust proxy
 * that avoids reading the masked field text (§5.3: no plaintext assertions). No real passwords.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SetPasswordStateRetentionAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteredPasswordSurvivesSavedStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var pw by rememberSaveable { mutableStateOf("") }
            CryptasaTheme {
                SetPasswordScreen(value = pw, onValueChange = { pw = it }, onNext = {}, onBack = {})
            }
        }

        // Before input: continue disabled.
        composeRule.onNodeWithTag(OnboardingTestTags.PASSWORD_CONTINUE).assertIsNotEnabled()

        // Enter a valid password → continue enabled.
        composeRule.onNode(hasSetTextAction()).performTextInput("Abcdefg1!")
        composeRule.onNodeWithTag(OnboardingTestTags.PASSWORD_CONTINUE).assertIsEnabled()

        // Simulate activity recreation / process restore.
        restorationTester.emulateSavedInstanceStateRestore()

        // Password survived → continue still enabled.
        composeRule.onNodeWithTag(OnboardingTestTags.PASSWORD_CONTINUE).assertIsEnabled()
    }
}
