package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-20 — ONB-2 ChoosePath behaviour on **Desktop** (`runComposeUiTest`, ADR-0011).
 *  - AK1: each card invokes the *right* callback (`onCreate` vs `onImport`) — Maestro only proves
 *    "left ChoosePath" on the placeholder destination.
 *  - AK2: the offline banner is driven by the `isOffline` param (state-injected); creating a wallet
 *    works offline, so the create card stays enabled. The banner is unreachable at runtime — the
 *    Android `observeConnectivity()` is a stub (always online) — so it's verified here, not in Maestro.
 *  - light + dark render the contract and the mode swap is token-only (layout invariant).
 */
@OptIn(ExperimentalTestApi::class)
class PathScreenDesktopTest {

    @Test
    fun createCardInvokesOnCreate() = runComposeUiTest {
        var creates = 0
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PathScreen(onCreate = { creates++ }, onImport = {}, onBack = {})
            }
        }
        onNodeWithTag(OnboardingTestTags.PATH_CREATE).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(1, creates, "PATH_CREATE must invoke onCreate")
    }

    @Test
    fun importCardInvokesOnImport() = runComposeUiTest {
        var imports = 0
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PathScreen(onCreate = {}, onImport = { imports++ }, onBack = {})
            }
        }
        onNodeWithTag(OnboardingTestTags.PATH_IMPORT).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(1, imports, "PATH_IMPORT must invoke onImport")
    }

    @Test
    fun offlineBannerShownWhenOffline_andCreateStaysEnabled() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PathScreen(onCreate = {}, onImport = {}, onBack = {}, isOffline = true)
            }
        }
        onNodeWithTag(OnboardingTestTags.PATH_OFFLINE_BANNER).assertIsDisplayed()
        // "Erstellen bleibt aktiv": creating a wallet works offline.
        onNodeWithTag(OnboardingTestTags.PATH_CREATE).assertIsDisplayed().assertIsEnabled().assertHasClickAction()
    }

    @Test
    fun noOfflineBannerWhenOnline() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PathScreen(onCreate = {}, onImport = {}, onBack = {}, isOffline = false)
            }
        }
        onNodeWithTag(OnboardingTestTags.PATH_OFFLINE_BANNER).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.PATH_CREATE).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.PATH_IMPORT).assertIsDisplayed()
    }

    @Test
    fun pathRendersContractInLightAndDarkWithoutRelayout() = runComposeUiTest {
        val mode = mutableStateOf(ThemeMode.Light)
        setContent {
            CryptasaTheme(mode.value) {
                PathScreen(onCreate = {}, onImport = {}, onBack = {})
            }
        }
        waitForIdle()
        val tags = listOf(OnboardingTestTags.PATH_CREATE, OnboardingTestTags.PATH_IMPORT)
        tags.forEach { onNodeWithTag(it).assertIsDisplayed() }
        val light = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        runOnIdle { mode.value = ThemeMode.Dark }
        waitForIdle()
        for (tag in tags) {
            onNodeWithTag(tag).assertIsDisplayed()
            val l = light.getValue(tag)
            val d = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals(l.left.value, d.left.value, 0.01f, "[$tag] left moved on Light→Dark")
            assertEquals(l.top.value, d.top.value, 0.01f, "[$tag] top moved on Light→Dark")
            assertEquals(l.right.value, d.right.value, 0.01f, "[$tag] right moved on Light→Dark")
            assertEquals(l.bottom.value, d.bottom.value, 0.01f, "[$tag] bottom moved on Light→Dark")
        }
    }
}
