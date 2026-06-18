package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection as UiLayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.WelcomeScreen
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-9 AK5 — ONB-1 Welcome under configuration changes on the Android renderer (Robolectric,
 * ADR-0013): the two contract actions survive Size-Class (compact/expanded), FontScale, Dark/Light
 * and RTL, and a Dark↔Light swap is a token-only change (layout bounds invariant). Each override
 * recomposes the screen, so remembered state (e.g. the sheet scroll position) is retained across the
 * change. RTL *mirroring* of the adaptive scaffold is covered by [OnboardingI18nRtlAndroidTest];
 * here we assert the welcome itself holds. Selection is ID-based ([OnboardingTestTags]).
 *
 * (A dedicated saved-instance-state restore test would use `StateRestorationTester` from
 * `ui-test-junit4`, which isn't on the module's test classpath — ONB-1 holds no user-entered state,
 * so it is deferred to the input-bearing screens; see the KAN-9 notes.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class WelcomeConfigChangeAndroidTest {

    private val compact = DpSize(360.dp, 640.dp)   // phone portrait → Compact width
    private val expanded = DpSize(900.dp, 1200.dp) // large window → Expanded width

    @Test
    fun welcomeHoldsAcrossCompactAndExpandedSizeClasses() = runComposeUiTest {
        val size = mutableStateOf(compact)
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                CryptasaTheme { WelcomeScreen(onStart = {}, onImport = {}) }
            }
        }
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()

        runOnIdle { size.value = expanded }
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()
    }

    @Test
    fun welcomeHoldsUnderLargeFontScale() = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then DeviceConfigurationOverride.FontScale(1.5f),
            ) {
                CryptasaTheme { WelcomeScreen(onStart = {}, onImport = {}) }
            }
        }
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()
    }

    @Test
    fun welcomeHoldsUnderRtl() = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then
                    DeviceConfigurationOverride.LayoutDirection(UiLayoutDirection.Rtl),
            ) {
                CryptasaTheme { WelcomeScreen(onStart = {}, onImport = {}) }
            }
        }
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()
    }

    @Test
    fun darkLightSwapKeepsWelcomeLayout() = runComposeUiTest {
        val dark = mutableStateOf(false)
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then DeviceConfigurationOverride.DarkMode(dark.value),
            ) {
                CryptasaTheme { WelcomeScreen(onStart = {}, onImport = {}) }
            }
        }
        waitForIdle()
        val tags = listOf(OnboardingTestTags.WELCOME_START, OnboardingTestTags.WELCOME_IMPORT)
        val light = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        runOnIdle { dark.value = true }
        waitForIdle()
        for (tag in tags) {
            val l = light.getValue(tag)
            val d = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals(l.left.value, d.left.value, 0.01f, "[$tag] left moved on Dark swap")
            assertEquals(l.top.value, d.top.value, 0.01f, "[$tag] top moved on Dark swap")
            assertEquals(l.right.value, d.right.value, 0.01f, "[$tag] right moved on Dark swap")
            assertEquals(l.bottom.value, d.bottom.value, 0.01f, "[$tag] bottom moved on Dark swap")
        }
    }
}
