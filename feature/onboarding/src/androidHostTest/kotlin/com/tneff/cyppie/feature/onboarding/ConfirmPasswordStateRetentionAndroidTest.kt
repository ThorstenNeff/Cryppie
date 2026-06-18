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
import com.tneff.cyppie.feature.onboarding.ui.ConfirmPasswordScreen
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * KAN-24 AK4 / AK3 — the **entered confirmation survives a saved-state restore** (rotation /
 * recreation), so there is no data loss (incl. on back, since the value is flow-VM-backed). Modelled
 * with a `rememberSaveable` holder; survival asserted via the continue gate (enabled ⇔ confirm
 * matches the password) — no plaintext is read (§5.3). No real passwords.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ConfirmPasswordStateRetentionAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteredConfirmationSurvivesSavedStateRestore() {
        val original = "Abcdefg1!"
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var confirm by rememberSaveable { mutableStateOf("") }
            CryptasaTheme {
                ConfirmPasswordScreen(
                    value = confirm,
                    password = original,
                    onValueChange = { confirm = it },
                    onNext = {},
                    onBack = {},
                )
            }
        }
        // Empty confirm → continue disabled.
        composeRule.onNodeWithTag(OnboardingTestTags.CONFIRM_CONTINUE).assertIsNotEnabled()

        // Type the matching confirmation → continue enabled.
        composeRule.onNode(hasSetTextAction()).performTextInput(original)
        composeRule.onNodeWithTag(OnboardingTestTags.CONFIRM_CONTINUE).assertIsEnabled()

        // Restore → confirmation survived → continue still enabled (no data loss).
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(OnboardingTestTags.CONFIRM_CONTINUE).assertIsEnabled()
    }
}
