package com.tneff.cyppie.feature.onboarding

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.WelcomeScreen
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * KAN-94 — Welcome **adaptive layout** repeatable coverage on the Android renderer (Robolectric,
 * ADR-0012), complementing the one-off `adb wm size` device check: in landscape (short height),
 * Medium and Expanded widths the content is **width-clamped to ≤480 dp** (centred, no body/CTA
 * overlap), and the primary action stays reachable. Deterministic via `DeviceConfigurationOverride`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class WelcomeAdaptiveAndroidTest {

    private fun assertStartClampedTo480(size: DpSize) = runComposeUiTest {
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                CryptasaTheme { WelcomeScreen(onStart = {}, onImport = {}) }
            }
        }
        // Primary action is laid out (reachable) and its width never exceeds the 480 dp content clamp,
        // even on a much wider window → content stays centred, not stretched edge-to-edge.
        val b = onNodeWithTag(OnboardingTestTags.WELCOME_START).assertExists().getUnclippedBoundsInRoot()
        val width = b.right - b.left
        assertTrue(width <= 480.dp, "WELCOME_START width $width must be ≤ 480 dp (clamp) for $size")
    }

    @Test fun expandedWidthLandscapeClampsAndCentres() = assertStartClampedTo480(DpSize(900.dp, 420.dp))

    @Test fun mediumWidthTallClampsAndCentres() = assertStartClampedTo480(DpSize(700.dp, 900.dp))

    @Test
    fun tallPortraitShowsActionsWithinClamp() = runComposeUiTest {
        // Tall narrow portrait keeps the hero/sheet path; actions still within the 480 dp sheet.
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))) {
                CryptasaTheme { WelcomeScreen(onStart = {}, onImport = {}) }
            }
        }
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()
        val b = onNodeWithTag(OnboardingTestTags.WELCOME_START).getUnclippedBoundsInRoot()
        assertTrue(b.right - b.left <= 480.dp)
    }
}
