package com.tneff.cyppie.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AK1/AK4 + ADR-0013 (KAN-7) — foundation components on **Android** via Robolectric (JVM, no emulator).
 *
 * Mirrors the Desktop suite on the Android renderer and adds a configuration-change check
 * (`DeviceConfigurationOverride.DarkMode`) proving the layout is invariant under a *system* dark
 * config — only tokens change — which Maestro cannot do per screen (ADR-0013). The host-test
 * `AndroidManifest.xml` registers `ComponentActivity` so `runComposeUiTest` can launch a host;
 * `GraphicsMode.NATIVE` enables Compose drawing under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ComponentStatesAndroidTest {

    private val tags = listOf("btn", "field", "banner")

    @Composable
    private fun Sample(mode: ThemeMode) {
        CryptasaTheme(mode) {
            Column(
                modifier = Modifier.width(360.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CryptasaButton("Continue", onClick = {}, modifier = Modifier.testTag("btn"))
                CryptasaTextField(
                    value = "abc",
                    onValueChange = {},
                    label = "Recovery phrase",
                    errorText = "Recovery phrase is invalid",
                    modifier = Modifier.testTag("field"),
                )
                CryptasaBanner(
                    title = "You are offline",
                    description = "Reconnect to continue",
                    tone = CryptasaBannerTone.Offline,
                    modifier = Modifier.testTag("banner"),
                )
            }
        }
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
        onNodeWithTag("btn").assertIsNotEnabled().performClick()
        assertEquals(0, clicks, "Disabled button must not invoke onClick")
    }

    @Test
    fun textFieldErrorExposesErrorSemanticsAndMessage() = runComposeUiTest {
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
        val errorNodes = onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, message),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(errorNodes.isNotEmpty(), "Field must expose error semantics carrying the message")
        onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun appModeSwitchKeepsLayoutBounds() = runComposeUiTest {
        val mode = mutableStateOf(ThemeMode.Light)
        setContent { Sample(mode.value) }
        waitForIdle()
        val light = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        runOnIdle { mode.value = ThemeMode.Dark }
        waitForIdle()
        val dark = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        assertBoundsEqual(light, dark, "in-app mode switch")
    }

    @Test
    fun systemDarkConfigChangeKeepsLayoutBounds() = runComposeUiTest {
        // ThemeMode.System follows the system config; toggling DarkMode must not move anything (ADR-0013).
        val dark = mutableStateOf(false)
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(dark.value)) {
                Sample(ThemeMode.System)
            }
        }
        waitForIdle()
        val lightBounds = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        runOnIdle { dark.value = true }
        waitForIdle()
        val darkBounds = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        assertBoundsEqual(lightBounds, darkBounds, "system dark config change")
    }

    private fun assertBoundsEqual(
        light: Map<String, androidx.compose.ui.unit.DpRect>,
        dark: Map<String, androidx.compose.ui.unit.DpRect>,
        label: String,
    ) {
        for (tag in tags) {
            val l = light.getValue(tag)
            val d = dark.getValue(tag)
            assertEquals(l.left.value, d.left.value, 0.01f, "[$tag] left moved on $label")
            assertEquals(l.top.value, d.top.value, 0.01f, "[$tag] top moved on $label")
            assertEquals(l.right.value, d.right.value, 0.01f, "[$tag] right moved on $label")
            assertEquals(l.bottom.value, d.bottom.value, 0.01f, "[$tag] bottom moved on $label")
        }
    }
}
