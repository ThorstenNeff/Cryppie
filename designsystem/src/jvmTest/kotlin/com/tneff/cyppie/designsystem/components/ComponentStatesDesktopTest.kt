package com.tneff.cyppie.designsystem.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AK1 (KAN-7) — foundation component states on **Desktop** (`runComposeUiTest`, ADR-0011).
 *
 * Behaviour, not pixels: we assert the semantics the framework and screen readers rely on. Selection
 * is by a test-set `testTag` — the components expose no production tags (those come from the Dev-Agent
 * per screen). The **pressed** state is asserted as a layout-invariant interaction (a colour-only
 * token swap); its exact pressed *colour* is left to visual/Maestro checks because CryptasaButton owns
 * its `MutableInteractionSource` internally and does not expose it for injection here.
 */
@OptIn(ExperimentalTestApi::class)
class ComponentStatesDesktopTest {

    @Test
    fun buttonDefaultIsEnabledClickableAndInvokesOnClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                CryptasaButton(text = "Continue", onClick = { clicks++ }, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
        assertEquals(1, clicks, "Enabled button must invoke onClick")
    }

    @Test
    fun buttonDisabledIsNotEnabledAndDoesNotInvokeOnClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                CryptasaButton(
                    text = "Continue",
                    onClick = { clicks++ },
                    enabled = false,
                    modifier = Modifier.testTag("btn"),
                )
            }
        }
        // Compose keeps the OnClick action but marks the node Disabled; clicking must be a no-op.
        onNodeWithTag("btn").assertIsNotEnabled().performClick()
        assertEquals(0, clicks, "Disabled button must not invoke onClick")
    }

    @Test
    fun buttonPressedKeepsLayoutColourOnlySwap() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                CryptasaButton(text = "Continue", onClick = {}, modifier = Modifier.testTag("btn"))
            }
        }
        val before = onNodeWithTag("btn").getUnclippedBoundsInRoot()
        // Hold the press: pressed state active (primary → primaryHover), layout must not move.
        onNodeWithTag("btn").performTouchInput { down(center) }
        waitForIdle()
        val pressed = onNodeWithTag("btn").getUnclippedBoundsInRoot()
        onNodeWithTag("btn").performTouchInput { up() }

        assertEquals(before.left.value, pressed.left.value, 0.01f, "pressed must not move left edge")
        assertEquals(before.top.value, pressed.top.value, 0.01f, "pressed must not move top edge")
        assertEquals(before.right.value, pressed.right.value, 0.01f, "pressed must not move right edge")
        assertEquals(before.bottom.value, pressed.bottom.value, 0.01f, "pressed must not move bottom edge")
    }

    @Test
    fun textFieldErrorSetsErrorSemanticsLiveRegionAndShowsMessage() = runComposeUiTest {
        val message = "Recovery phrase is invalid"
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                CryptasaTextField(
                    value = "",
                    onValueChange = {},
                    label = "Recovery phrase",
                    errorText = message,
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        // error()/liveRegion sit on inner nodes of the field subtree — assert across the unmerged tree.
        val errorNodes = onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, message),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(errorNodes.isNotEmpty(), "Field must expose error semantics carrying the message")

        val liveRegions = onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(liveRegions.isNotEmpty(), "Error message must be a live region for screen readers")

        onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun textFieldWithoutErrorHasNoErrorSemantics() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                CryptasaTextField(
                    value = "abc",
                    onValueChange = {},
                    label = "Recovery phrase",
                    errorText = null,
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        val errorNodes = onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Error),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(errorNodes.isEmpty(), "No error semantics may be present without errorText")
    }

    @Test
    fun textFieldGainsFocusWhenRequested() = runComposeUiTest {
        val text = mutableStateOf("")
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                CryptasaTextField(
                    value = text.value,
                    onValueChange = { text.value = it },
                    label = "Recovery phrase",
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        // The editable node is the inner BasicTextField (carries the SetText action).
        onNode(hasSetTextAction()).requestFocus().assertIsFocused()
    }
}
