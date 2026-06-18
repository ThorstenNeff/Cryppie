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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection as UiLayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.ImportSeedScreen
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-26 AK — ONB-5 ImportSeed under configuration changes on the Android renderer (Robolectric,
 * ADR-0013): the 12/24 selector + word grid + "Import" survive Size-Class, large FontScale, RTL and
 * Dark/Light; a Dark↔Light swap is token-only (import-button bounds invariant); and entered words
 * (hoisted state) are retained across a config recomposition — no data loss on rotation. ID-based.
 * (Process-death/saved-instance restore of the phrase → [ImportSeedStateRetentionAndroidTest].)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ImportSeedConfigChangeAndroidTest {

    private val compact = DpSize(360.dp, 720.dp)
    private val expanded = DpSize(900.dp, 1200.dp)
    private val validWords =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")

    private fun blank() = List(12) { "" }

    @Test
    fun gridSurvivesCompactAndExpandedSizeClasses() = runComposeUiTest {
        val size = mutableStateOf(compact)
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                CryptasaTheme { ImportSeedScreen(12, blank(), {}, { _, _ -> }, {}, {}) }
            }
        }
        onNodeWithTag(OnboardingTestTags.SEED_WORDCOUNT_12).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.seedCell(1)).assertIsDisplayed()
        size.value = expanded
        onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.seedCell(12)).assertIsDisplayed()
    }

    @Test
    fun gridSurvivesLargeFontScaleAndRtl() {
        runComposeUiTest {
            setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) {
                    CryptasaTheme { ImportSeedScreen(12, blank(), {}, { _, _ -> }, {}, {}) }
                }
            }
            onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsDisplayed()
        }
        runComposeUiTest {
            setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.LayoutDirection(UiLayoutDirection.Rtl)) {
                    CryptasaTheme { ImportSeedScreen(12, blank(), {}, { _, _ -> }, {}, {}) }
                }
            }
            onNodeWithTag(OnboardingTestTags.SEED_WORDCOUNT_24).assertIsDisplayed()
            onNodeWithTag(OnboardingTestTags.seedCell(1)).assertIsDisplayed()
        }
    }

    @Test
    fun darkLightSwapKeepsImportButtonBounds() {
        fun bounds(dark: Boolean) = run {
            var b = androidx.compose.ui.unit.DpRect(0.dp, 0.dp, 0.dp, 0.dp)
            runComposeUiTest {
                setContent {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(dark)) {
                        CryptasaTheme { ImportSeedScreen(12, blank(), {}, { _, _ -> }, {}, {}) }
                    }
                }
                b = onNodeWithTag(OnboardingTestTags.SEED_IMPORT).getUnclippedBoundsInRoot()
            }
            b
        }
        assertEquals(bounds(dark = false), bounds(dark = true), "Dark↔Light must be token-only")
    }

    @Test
    fun enteredWordsRetainedAcrossConfigRecomposition() = runComposeUiTest {
        val size = mutableStateOf(compact)
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                CryptasaTheme { ImportSeedScreen(12, validWords, {}, { _, _ -> }, {}, {}) }
            }
        }
        onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled()
        size.value = expanded // config change → recomposition
        onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled() // words not lost
    }
}
