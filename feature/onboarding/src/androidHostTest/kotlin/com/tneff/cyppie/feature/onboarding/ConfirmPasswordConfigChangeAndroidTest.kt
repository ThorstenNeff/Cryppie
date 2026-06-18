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
import com.tneff.cyppie.feature.onboarding.ui.ConfirmPasswordScreen
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-24 AK4 (layout) — ONB-4 ConfirmPassword under configuration changes (Robolectric, ADR-0013):
 * input + continue survive Size-Class/FontScale/RTL/Dark-Light, and a Dark↔Light swap is token-only
 * (bounds invariant). Saved-state restore is in [ConfirmPasswordStateRetentionAndroidTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ConfirmPasswordConfigChangeAndroidTest {

    private val compact = DpSize(360.dp, 720.dp)
    private val expanded = DpSize(900.dp, 1200.dp)
    private val pw = "Abcdefg1!" // a matching confirm → continue enabled, full screen rendered
    private val keys = listOf(OnboardingTestTags.CONFIRM_INPUT, OnboardingTestTags.CONFIRM_CONTINUE)

    @Test
    fun holdsAcrossSizeClasses() = runComposeUiTest {
        val size = mutableStateOf(compact)
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                CryptasaTheme { ConfirmPasswordScreen(value = pw, password = pw, onValueChange = {}, onNext = {}, onBack = {}) }
            }
        }
        waitForIdle()
        keys.forEach { onNodeWithTag(it).assertIsDisplayed() }
        runOnIdle { size.value = expanded }
        waitForIdle()
        keys.forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun holdsUnderLargeFontScale() = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then DeviceConfigurationOverride.FontScale(1.5f),
            ) {
                CryptasaTheme { ConfirmPasswordScreen(value = pw, password = pw, onValueChange = {}, onNext = {}, onBack = {}) }
            }
        }
        keys.forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun holdsUnderRtl() = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then
                    DeviceConfigurationOverride.LayoutDirection(UiLayoutDirection.Rtl),
            ) {
                CryptasaTheme { ConfirmPasswordScreen(value = pw, password = pw, onValueChange = {}, onNext = {}, onBack = {}) }
            }
        }
        keys.forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun darkLightSwapKeepsLayout() = runComposeUiTest {
        val dark = mutableStateOf(false)
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then DeviceConfigurationOverride.DarkMode(dark.value),
            ) {
                CryptasaTheme { ConfirmPasswordScreen(value = pw, password = pw, onValueChange = {}, onNext = {}, onBack = {}) }
            }
        }
        waitForIdle()
        val light = keys.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }
        runOnIdle { dark.value = true }
        waitForIdle()
        for (tag in keys) {
            val l = light.getValue(tag)
            val d = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals(l.left.value, d.left.value, 0.01f, "[$tag] left moved on Dark swap")
            assertEquals(l.top.value, d.top.value, 0.01f, "[$tag] top moved on Dark swap")
            assertEquals(l.right.value, d.right.value, 0.01f, "[$tag] right moved on Dark swap")
            assertEquals(l.bottom.value, d.bottom.value, 0.01f, "[$tag] bottom moved on Dark swap")
        }
    }
}
