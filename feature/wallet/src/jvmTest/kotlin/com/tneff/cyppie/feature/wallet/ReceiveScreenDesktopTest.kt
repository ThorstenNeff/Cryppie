package com.tneff.cyppie.feature.wallet

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import kotlin.test.Test

/**
 * KAN-78 — Receive screen behaviour on Desktop (`runComposeUiTest`, ADR-0011). Verifies the spec
 * surface: chain segments, QR, full (untruncated) address, warning, and the copy → copied state.
 */
@OptIn(ExperimentalTestApi::class)
class ReceiveScreenDesktopTest {

    private val address = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"

    @Test
    fun rendersAllReceiveElements() = runComposeUiTest {
        setContent { CryptasaTheme(ThemeMode.Light) { ReceiveScreen(address = address, onBack = {}) } }
        listOf(
            WalletTestTags.RECEIVE_CHAIN,
            WalletTestTags.RECEIVE_QR,
            WalletTestTags.RECEIVE_ADDRESS,
            WalletTestTags.RECEIVE_COPY,
            WalletTestTags.RECEIVE_WARNING,
        ).forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun showsFullAddressNeverTruncated() = runComposeUiTest {
        setContent { CryptasaTheme(ThemeMode.Light) { ReceiveScreen(address = address, onBack = {}) } }
        onNodeWithTag(WalletTestTags.RECEIVE_ADDRESS).assertTextEquals(address)
    }

    @Test
    fun copyButtonSwitchesToCopiedState() = runComposeUiTest {
        setContent { CryptasaTheme(ThemeMode.Light) { ReceiveScreen(address = address, onBack = {}) } }
        onNodeWithText("Copy address").assertIsDisplayed() // button label, en default
        onNodeWithTag(WalletTestTags.RECEIVE_COPY).performClick()
        onNodeWithText("Copied").assertIsDisplayed()
    }
}
