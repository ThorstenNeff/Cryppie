package com.tneff.cyppie.feature.wallet

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.evm.network.NetworkEnvironment
import kotlin.test.Test

/**
 * KAN-176 (PRD-09) — Settings switch-warning **active-sessions note** (the KAN-173 safety hint). This
 * is the one branch the on-device E2E can't reach: the count comes from the backend user-service
 * (`copyApi.listCopySessions()` + `dcaApi.listSessions()` against `auth.cyppie.com`), unreachable from
 * the emulator, so the `count > 0` display path is verified here deterministically by injecting
 * `loadActiveSessionCount`. `CryptasaDialog` is an inline overlay → captured by `runComposeUiTest`.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsSwitchWarningDesktopTest {

    @Test
    fun activeSessionsNote_shown_whenCountPositive() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                SettingsScreen(
                    activeEnv = NetworkEnvironment.MAINNET,
                    onSwitchNetwork = {},
                    onBack = {},
                    loadActiveSessionCount = { 2 },
                )
            }
        }
        waitForIdle() // let the LaunchedEffect resolve activeSessionCount = 2
        // Open the switch-warning dialog (Mainnet→Testnet).
        onNodeWithTag("net_testnet").performClick()
        // Base framing present...
        onNodeWithText("no real money is used", substring = true).assertIsDisplayed()
        // ...and the active-sessions safety note fires (count > 0).
        onNodeWithText("active sessions keep running", substring = true).assertIsDisplayed()
    }

    @Test
    fun activeSessionsNote_omitted_whenCountZero() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                SettingsScreen(
                    activeEnv = NetworkEnvironment.MAINNET,
                    onSwitchNetwork = {},
                    onBack = {},
                    loadActiveSessionCount = { 0 },
                )
            }
        }
        waitForIdle()
        onNodeWithTag("net_testnet").performClick()
        // Dialog opens with the base framing, but NO active-sessions note (count == 0).
        onNodeWithText("no real money is used", substring = true).assertIsDisplayed()
        onNodeWithText("active sessions keep running", substring = true).assertDoesNotExist()
    }
}
