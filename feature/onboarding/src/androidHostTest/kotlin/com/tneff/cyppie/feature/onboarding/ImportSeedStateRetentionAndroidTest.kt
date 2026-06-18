package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ui.ImportSeedScreen
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * KAN-26 AK — the ImportSeed screen state must **survive saved-instance-state restore** (process
 * death / Activity recreation), so a user never loses progress on rotation (ADR-0013). Both the
 * 12↔24 selection and the entered phrase live in `rememberSaveable` host state (as the flow
 * ViewModel persists them); after restore the grid still shows 24 cells and "Import" is still
 * enabled for the (restored) valid phrase. Public test mnemonic only (24× "abandon" — all valid
 * BIP-39 words) — no real seeds (§5.3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImportSeedStateRetentionAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionAndPhraseSurviveSavedInstanceStateRestore() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            var wordCount by rememberSaveable { mutableStateOf(12) }
            val words = rememberSaveable(
                saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
            ) { List(24) { "abandon" }.toMutableStateList() }
            CryptasaTheme {
                ImportSeedScreen(
                    wordCount = wordCount,
                    words = words,
                    onWordCountChange = { wordCount = it },
                    onWordChange = { i, w -> if (i < words.size) words[i] = w },
                    onImport = {},
                    onBack = {},
                )
            }
        }

        // 12 valid words → import enabled; cell 13 absent.
        composeRule.onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled()
        composeRule.onNodeWithTag(OnboardingTestTags.seedCell(13)).assertDoesNotExist()

        // Switch to 24 → grid grows, still all valid.
        composeRule.onNodeWithTag(OnboardingTestTags.SEED_WORDCOUNT_24).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.seedCell(13)).assertExists()
        composeRule.onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled()

        // Process death + restore — selection (24) and phrase persist.
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(OnboardingTestTags.seedCell(13)).assertExists()
        composeRule.onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled()
    }
}
