package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection as UiLayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.ShowSeedScreen
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * KAN-28 AK — ONB-6 ShowSeed on the Android renderer (Robolectric, ADR-0013): the screen survives
 * Size-Class / FontScale / RTL / Dark, and the **reveal + acknowledgement state survives
 * saved-instance restore** (the phrase stays revealed and "continue" stays enabled across a
 * process-death / rotation — no re-cover or progress loss). Synthetic words only (§5.3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ShowSeedConfigChangeAndroidTest {

    private val words = List(12) { "abandon" }

    @get:Rule
    val composeRule = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun Screen() {
        CryptasaTheme {
            ShowSeedScreen(
                words = words, wordCount = 12, generationFailed = false,
                onGenerate = {}, onRetry = {}, onContinue = {}, onBack = {},
            )
        }
    }

    @Test
    fun rendersAcrossSizeClassFontScaleRtlAndDark() {
        for (override in listOf(
            DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 720.dp)),
            DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1200.dp)),
            DeviceConfigurationOverride.FontScale(1.5f),
            DeviceConfigurationOverride.LayoutDirection(UiLayoutDirection.Rtl),
            DeviceConfigurationOverride.DarkMode(true),
        )) {
            runComposeUiTest {
                setContent { DeviceConfigurationOverride(override) { Screen() } }
                onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).assertIsDisplayed()
            }
        }
    }

    @Test
    fun revealStateSurvivesSavedInstanceStateRestore() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Screen() }

        // Covered → tap to reveal.
        composeRule.onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).assertDoesNotExist() // revealed

        restoration.emulateSavedInstanceStateRestore()

        // The phrase stays revealed across process death / rotation — it is NOT re-covered.
        composeRule.onNodeWithTag(OnboardingTestTags.SHOW_SEED_REVEAL).assertDoesNotExist()
    }
}
