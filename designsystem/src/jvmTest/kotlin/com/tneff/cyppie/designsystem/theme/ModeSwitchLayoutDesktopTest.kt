package com.tneff.cyppie.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AK4 (KAN-7) — switching [ThemeMode] swaps token **values only**, never layout (PRD-00 FR-2).
 *
 * One composition holds a mode flag; we render a representative slice of foundation components,
 * capture each tagged node's bounds in Light, flip the flag to Dark, and assert the bounds are
 * byte-identical. Anything but a colour change (different padding, font metrics, conditional nodes)
 * would move a node and fail. Desktop variant (`runComposeUiTest`); the Android/Robolectric variant
 * mirrors it under config qualifiers.
 */
@OptIn(ExperimentalTestApi::class)
class ModeSwitchLayoutDesktopTest {

    private val tags = listOf("btn", "field", "banner")
    private val tolerance = 0.01f

    @Test
    fun modeSwitchKeepsLayoutBounds() = runComposeUiTest {
        val mode = mutableStateOf(ThemeMode.Light)
        setContent {
            CryptasaTheme(mode.value) {
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
        waitForIdle()
        val light = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        runOnIdle { mode.value = ThemeMode.Dark }
        waitForIdle()
        val dark = tags.associateWith { onNodeWithTag(it).getUnclippedBoundsInRoot() }

        for (tag in tags) {
            val l = light.getValue(tag)
            val d = dark.getValue(tag)
            // left/top/right/bottom fully pin position *and* size — a token-only swap moves none.
            assertEquals(l.left.value, d.left.value, tolerance, "[$tag] left moved on mode switch")
            assertEquals(l.top.value, d.top.value, tolerance, "[$tag] top moved on mode switch")
            assertEquals(l.right.value, d.right.value, tolerance, "[$tag] right moved on mode switch")
            assertEquals(l.bottom.value, d.bottom.value, tolerance, "[$tag] bottom moved on mode switch")
        }
    }
}
