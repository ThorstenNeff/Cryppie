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
import com.tneff.cyppie.feature.onboarding.ui.PathScreen
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-20 AK3 — ONB-2 ChoosePath under configuration changes on the Android renderer (Robolectric,
 * ADR-0013): the two cards survive Size-Class (compact/expanded), FontScale, Dark/Light and RTL, a
 * Dark↔Light swap is token-only (layout bounds invariant), and the offline banner still renders
 * under a config change. Each override recomposes, so remembered state (the scroll position) is
 * retained. RTL scaffold mirroring is covered by [OnboardingI18nRtlAndroidTest]. ID-based selection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class PathConfigChangeAndroidTest {

    private val compact = DpSize(360.dp, 640.dp)
    private val expanded = DpSize(900.dp, 1200.dp)
    private val cards = listOf(OnboardingTestTags.PATH_CREATE, OnboardingTestTags.PATH_IMPORT)

    @Test
    fun pathHoldsAcrossCompactAndExpandedSizeClasses() = runComposeUiTest {
        val size = mutableStateOf(compact)
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                CryptasaTheme { PathScreen(onCreate = {}, onImport = {}, onBack = {}) }
            }
        }
        waitForIdle()
        cards.forEach { onNodeWithTag(it).assertIsDisplayed() }

        runOnIdle { size.value = expanded }
        waitForIdle()
        cards.forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun pathHoldsUnderLargeFontScale() = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then DeviceConfigurationOverride.FontScale(1.5f),
            ) {
                CryptasaTheme { PathScreen(onCreate = {}, onImport = {}, onBack = {}) }
            }
        }
        cards.forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun pathHoldsUnderRtl() = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then
                    DeviceConfigurationOverride.LayoutDirection(UiLayoutDirection.Rtl),
            ) {
                CryptasaTheme { PathScreen(onCreate = {}, onImport = {}, onBack = {}) }
            }
        }
        cards.forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun offlineBannerRendersUnderDarkConfig() = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then DeviceConfigurationOverride.DarkMode(true),
            ) {
                CryptasaTheme { PathScreen(onCreate = {}, onImport = {}, onBack = {}, isOffline = true) }
            }
        }
        onNodeWithTag(OnboardingTestTags.PATH_OFFLINE_BANNER).assertIsDisplayed()
        cards.forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun darkLightSwapKeepsPathLayout() = runComposeUiTest {
        val dark = mutableStateOf(false)
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(compact) then DeviceConfigurationOverride.DarkMode(dark.value),
            ) {
                CryptasaTheme { PathScreen(onCreate = {}, onImport = {}, onBack = {}) }
            }
        }
        waitForIdle()
        val light = cards.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        runOnIdle { dark.value = true }
        waitForIdle()
        for (tag in cards) {
            val l = light.getValue(tag)
            val d = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals(l.left.value, d.left.value, 0.01f, "[$tag] left moved on Dark swap")
            assertEquals(l.top.value, d.top.value, 0.01f, "[$tag] top moved on Dark swap")
            assertEquals(l.right.value, d.right.value, 0.01f, "[$tag] right moved on Dark swap")
            assertEquals(l.bottom.value, d.bottom.value, 0.01f, "[$tag] bottom moved on Dark swap")
        }
    }
}
