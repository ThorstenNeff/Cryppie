package com.tneff.cyppie.feature.wallet

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.walletcore.Erc20Token
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.TokenResolution
import kotlin.test.Test
import kotlin.test.assertEquals

/** KAN-83 Stage 2 — AddTokenScreen behaviour on Desktop (`runComposeUiTest`). */
@OptIn(ExperimentalTestApi::class)
class AddTokenScreenDesktopTest {

    private val usdc = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359" // valid EIP-55
    private val token = Erc20Token(EvmAddress.parse(usdc), EvmChain.ETHEREUM, "USDC", 6)

    @Test
    fun resolvedShowsCardAndEnablesAdd() = runComposeUiTest {
        var added: Erc20Token? = null
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                AddTokenScreen(
                    chain = EvmChain.ETHEREUM,
                    onResolve = { _, _ -> TokenResolution.Resolved(token) },
                    onAdd = { added = it },
                    onBack = {},
                )
            }
        }
        onNode(hasSetTextAction()).performTextInput(usdc)
        onNodeWithTag(WalletTestTags.TOKEN_RESOLVED).assertIsDisplayed()
        onNodeWithTag(WalletTestTags.TOKEN_ADD).assertIsEnabled().performClick()
        assertEquals("USDC", added?.symbol)
        assertEquals(6, added?.decimals)
    }

    @Test
    fun notErc20ShowsErrorAndAddDisabled() = runComposeUiTest {
        // Valid EIP-55 address, but the resolver says it's not an ERC-20 (distinct from invalid input).
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                AddTokenScreen(EvmChain.ETHEREUM, { _, _ -> TokenResolution.NotErc20 }, {}, {})
            }
        }
        onNode(hasSetTextAction()).performTextInput(usdc)
        onNodeWithTag(WalletTestTags.TOKEN_ERROR).assertIsDisplayed()
        onNodeWithTag(WalletTestTags.TOKEN_ADD).assertIsNotEnabled()
    }

    @Test
    fun retryAfterNetworkErrorResolves() = runComposeUiTest {
        var calls = 0
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                AddTokenScreen(
                    chain = EvmChain.ETHEREUM,
                    onResolve = { _, _ -> if (calls++ == 0) TokenResolution.NetworkError else TokenResolution.Resolved(token) },
                    onAdd = {},
                    onBack = {},
                )
            }
        }
        onNode(hasSetTextAction()).performTextInput(usdc)
        onNodeWithTag(WalletTestTags.TOKEN_NETWORK).assertIsDisplayed() // 1st resolve → network error
        onNodeWithText("Try again").performClick() // retry (retryTick++)
        onNodeWithTag(WalletTestTags.TOKEN_RESOLVED).assertIsDisplayed() // 2nd resolve → resolved
    }

    @Test
    fun invalidEip55ShowsErrorAndAddDisabled() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                AddTokenScreen(EvmChain.ETHEREUM, { _, _ -> TokenResolution.NotErc20 }, {}, {})
            }
        }
        onNode(hasSetTextAction()).performTextInput("0x" + "G".repeat(40)) // 42 chars, non-hex → invalid
        onNodeWithTag(WalletTestTags.TOKEN_ERROR).assertIsDisplayed()
        onNodeWithTag(WalletTestTags.TOKEN_ADD).assertIsNotEnabled()
    }

    @Test
    fun networkErrorShowsRetryBanner() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                AddTokenScreen(EvmChain.ETHEREUM, { _, _ -> TokenResolution.NetworkError }, {}, {})
            }
        }
        onNode(hasSetTextAction()).performTextInput(usdc)
        onNodeWithTag(WalletTestTags.TOKEN_NETWORK).assertIsDisplayed()
        onNodeWithTag(WalletTestTags.TOKEN_ADD).assertIsNotEnabled()
    }
}
