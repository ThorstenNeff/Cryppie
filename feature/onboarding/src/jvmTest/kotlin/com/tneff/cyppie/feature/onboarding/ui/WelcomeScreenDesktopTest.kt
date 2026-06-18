package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
 * KAN-9 — ONB-1 Welcome behaviour on **Desktop** (`runComposeUiTest`, ADR-0011). Covers the parts
 * Maestro can't pin down on the placeholder destinations:
 *  - AK1: each action invokes the *right* callback (`onStart` vs `onImport`) — device-level Maestro
 *    only proves "left Welcome".
 *  - AK2: the blocking start-error dialog (state-injected; `verifyAppIntegrity()` stub is always
 *    true so it is unreachable at runtime — known gap, see the Maestro flow header).
 *  - AK4: the contract renders in light **and** dark and the mode swap is a token-only change
 *    (layout bounds invariant). Exact Figma pixel parity stays a visual/Maestro spot-check.
 */
@OptIn(ExperimentalTestApi::class)
class WelcomeScreenDesktopTest {

    @Test
    fun startActionInvokesOnStart() = runComposeUiTest {
        var starts = 0
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                WelcomeScreen(onStart = { starts++ }, onImport = {})
            }
        }
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(1, starts, "WELCOME_START must invoke onStart")
    }

    @Test
    fun importActionInvokesOnImport() = runComposeUiTest {
        var imports = 0
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                WelcomeScreen(onStart = {}, onImport = { imports++ })
            }
        }
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(1, imports, "WELCOME_IMPORT must invoke onImport")
    }

    @Test
    fun contentStateShowsNoErrorDialog() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                WelcomeScreen(onStart = {}, onImport = {}, state = WelcomeState.Content)
            }
        }
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_START_ERROR_DIALOG).assertDoesNotExist()
    }

    @Test
    fun startErrorShowsBlockingNonDismissableDialog() = runComposeUiTest {
        val state = mutableStateOf(WelcomeState.StartError)
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                // onCloseError is a no-op exactly like OnboardingRoot wires it: a corrupted install
                // stays blocked (real exit is platform-specific, ADR-0009).
                WelcomeScreen(onStart = {}, onImport = {}, state = state.value, onCloseError = {})
            }
        }
        val dialog = onNodeWithTag(OnboardingTestTags.WELCOME_START_ERROR_DIALOG)
        dialog.assertIsDisplayed()
        // Non-dismissable: interacting with the dialog (incl. its close action) does not remove it.
        dialog.performClick()
        onNodeWithTag(OnboardingTestTags.WELCOME_START_ERROR_DIALOG).assertIsDisplayed()
    }

    @Test
    fun welcomeRendersContractInLightAndDarkWithoutRelayout() = runComposeUiTest {
        val mode = mutableStateOf(ThemeMode.Light)
        setContent {
            CryptasaTheme(mode.value) {
                WelcomeScreen(onStart = {}, onImport = {})
            }
        }
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()
        val light = listOf(OnboardingTestTags.WELCOME_START, OnboardingTestTags.WELCOME_IMPORT)
            .associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        runOnIdle { mode.value = ThemeMode.Dark }
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertIsDisplayed()

        for ((tag, l) in light) {
            val d = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals(l.left.value, d.left.value, 0.01f, "[$tag] left moved on Light→Dark")
            assertEquals(l.top.value, d.top.value, 0.01f, "[$tag] top moved on Light→Dark")
            assertEquals(l.right.value, d.right.value, 0.01f, "[$tag] right moved on Light→Dark")
            assertEquals(l.bottom.value, d.bottom.value, 0.01f, "[$tag] bottom moved on Light→Dark")
        }
    }
}
