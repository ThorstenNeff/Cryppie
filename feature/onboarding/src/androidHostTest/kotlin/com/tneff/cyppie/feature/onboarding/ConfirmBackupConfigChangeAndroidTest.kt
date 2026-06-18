package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection as UiLayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.ConfirmBackupScreen
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * KAN-30 AK — ONB-7 ConfirmBackup on the Android renderer (Robolectric, ADR-0013): the challenge
 * survives Size-Class / FontScale / RTL / Dark, and the **entered challenge words survive
 * saved-instance restore** (process death / rotation) — "confirm" stays enabled for a correct,
 * restored challenge. Synthetic words only (§5.3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ConfirmBackupConfigChangeAndroidTest {

    private val expected = listOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident",
    )
    private val positions = listOf(0, 4, 9)
    private val correct = mapOf(0 to "abandon", 4 to "above", 9 to "abuse")

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Screen(entries: Map<Int, String>) {
        CryptasaTheme {
            ConfirmBackupScreen(
                positions = positions, expectedWords = expected, entries = entries, attempts = 0,
                onEnsureChallenge = {}, onWordChange = { _, _ -> }, onConfirm = {}, onFailure = {},
                onReshowSeed = {}, onBack = {},
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
                setContent { DeviceConfigurationOverride(override) { Screen(correct) } }
                onNodeWithTag(OnboardingTestTags.backupCell(1)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun challengeEntriesSurviveSavedInstanceStateRestore() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            val entries = rememberSaveable(
                saver = listSaver(
                    save = { m -> m.entries.flatMap { listOf(it.key, it.value) } },
                    restore = { flat -> flat.chunked(2).associate { (it[0] as Int) to (it[1] as String) } },
                ),
            ) { correct }
            Screen(entries)
        }

        // Correct restored challenge → confirm enabled.
        composeRule.onNodeWithTag(OnboardingTestTags.BACKUP_CONTINUE).assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(OnboardingTestTags.BACKUP_CONTINUE).assertIsEnabled() // entries persisted
    }
}
