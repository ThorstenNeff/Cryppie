package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-26 AK — ONB-5 ImportSeed gating + **On-Attempt checksum banner** on Desktop (`runComposeUiTest`).
 *
 * Contract (per the screen): "Import" enables once **every word is filled and a valid BIP-39 word**
 * (per-cell), NOT on checksum. The checksum is only enforced when *Import is pressed* — a bad
 * checksum then shows `onb_seed_error_banner` (no single cell blamed) and does **not** navigate.
 * Only public test mnemonics — no real seeds (§5.3).
 */
@OptIn(ExperimentalTestApi::class)
class ImportSeedScreenDesktopTest {

    private val abandonAbout = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
    private val allAbandon = List(12) { "abandon" }       // valid words, bad checksum
    private val oneInvalid = List(11) { "abandon" } + "zzzz"

    /** Renders ImportSeed over driven (wordCount, words) state; returns the import-click counter. */
    private fun runWith(
        initialWords: List<String>,
        block: ComposeAssertions.() -> Unit,
    ) = runComposeUiTest {
        var imports = 0
        var wordCount by mutableStateOf(initialWords.size)
        val words = mutableStateListOf(*initialWords.toTypedArray())
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                ImportSeedScreen(
                    wordCount = wordCount,
                    words = words,
                    onWordCountChange = { n ->
                        wordCount = n
                        while (words.size < n) words.add("")
                        while (words.size > n) words.removeAt(words.size - 1)
                    },
                    onWordChange = { i, w -> if (i < words.size) words[i] = w },
                    onImport = { imports++ },
                    onBack = {},
                )
            }
        }
        ComposeAssertions(this) { imports }.block()
    }

    private class ComposeAssertions(val t: androidx.compose.ui.test.ComposeUiTest, val imports: () -> Int)

    @Test
    fun importDisabledWhenAWordIsInvalidOrMissing() = runWith(oneInvalid) {
        t.onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun importEnabledWhenAllWordsValid_evenWithBadChecksum() = runWith(allAbandon) {
        // On-Attempt contract: every word is a valid BIP-39 word ⇒ import enabled (checksum not yet checked).
        t.onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled()
        t.onNodeWithTag(OnboardingTestTags.SEED_ERROR_BANNER).assertDoesNotExist()
    }

    @Test
    fun badChecksumShowsBannerOnAttempt_andDoesNotImport() = runWith(allAbandon) {
        t.onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled().performClick()
        t.onNodeWithTag(OnboardingTestTags.SEED_ERROR_BANNER).assertIsDisplayed()
        assertEquals(0, imports(), "bad checksum must not navigate")
    }

    @Test
    fun validPhraseImportsWithoutBanner() = runWith(abandonAbout) {
        t.onNodeWithTag(OnboardingTestTags.SEED_IMPORT).assertIsEnabled().performClick()
        t.onNodeWithTag(OnboardingTestTags.SEED_ERROR_BANNER).assertDoesNotExist()
        assertEquals(1, imports(), "valid checksum must invoke onImport")
    }

    @Test
    fun switchingTo24ShowsMoreCells() = runWith(abandonAbout) {
        t.onNodeWithTag(OnboardingTestTags.seedCell(13)).assertDoesNotExist()
        t.onNodeWithTag(OnboardingTestTags.SEED_WORDCOUNT_24).performClick()
        t.onNodeWithTag(OnboardingTestTags.seedCell(13)).assertIsDisplayed() // grid grew to 24
    }
}
