package com.tneff.cyppie.feature.wallet

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.rpc.NftItem
import com.tneff.cyppie.rpc.NftPage
import com.tneff.cyppie.rpc.NftReadClient
import com.tneff.cyppie.rpc.NftType
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletcore.AccountManager
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * KAN-105 — NFT grid behaviour on Desktop (`runComposeUiTest`, ADR-0011). The VM's first-load runs on
 * `viewModelScope`; an [UnconfinedTestDispatcher] as `Dispatchers.Main` makes it resolve synchronously
 * at construction (the fake client returns eagerly), so the screen renders a settled state — no clock
 * races. Asserts the three surfaces: Content (tile + degraded banner), Empty, and Error+retry.
 */
@OptIn(ExperimentalTestApi::class)
class NftGridScreenDesktopTest {

    private val bayc = EvmAddress.parse("0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D")

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun repo(nft: Map<Long, NftReadClient>): WalletRepository {
        val accountManager = AccountManager(
            EvmKeyManager(
                SeedSource.ofSeed(
                    Mnemonic.of("test test test test test test test test test test test junk").toSeed(),
                ),
            ),
        )
        return WalletRepository(accountManager, emptyMap(), nft)
    }

    private class FakeNftClient(private val page: NftPage) : NftReadClient {
        override val degraded = page.degraded
        override suspend fun nftsForOwner(owner: EvmAddress, pageKey: String?, pageSize: Int, excludeSpam: Boolean): NftPage = page
    }

    private fun item(tokenId: String, spam: Boolean = false) = NftItem(
        chainId = 1L, contract = bayc, tokenId = tokenId, type = NftType.ERC721,
        name = "Ape #$tokenId", collectionName = "BAYC", imageUrl = null, balance = null, isSpam = spam,
    )

    private fun vm(nft: Map<Long, NftReadClient>) =
        NftViewModel(repo(nft), accountIndex = 0, chain = EvmChain.ETHEREUM)

    @Test
    fun contentShowsTileAndDegradedBanner() = runComposeUiTest {
        // One real + one spam item on a degraded page: spam filtered out, banner shown, tile present.
        val client = FakeNftClient(NftPage(listOf(item("1"), item("9", spam = true)), nextPageKey = null, totalCount = 1, degraded = true))
        val viewModel = vm(mapOf(EvmChain.ETHEREUM.chainId to client))
        setContent { CryptasaTheme(ThemeMode.Light) { NftGridScreen(onBack = {}, viewModel = viewModel) } }
        onNodeWithTag(WalletTestTags.NFT_GRID).assertIsDisplayed()
        onNodeWithTag(WalletTestTags.NFT_DEGRADED_BANNER).assertIsDisplayed()
        onNodeWithTag(WalletTestTags.nftItem(bayc.value, "1")).assertIsDisplayed()
    }

    @Test
    fun emptyPageShowsEmptyState() = runComposeUiTest {
        val client = FakeNftClient(NftPage(emptyList(), nextPageKey = null, totalCount = 0, degraded = false))
        val viewModel = vm(mapOf(EvmChain.ETHEREUM.chainId to client))
        setContent { CryptasaTheme(ThemeMode.Light) { NftGridScreen(onBack = {}, viewModel = viewModel) } }
        onNodeWithTag(WalletTestTags.NFT_EMPTY).assertIsDisplayed()
    }

    @Test
    fun unconfiguredChainShowsErrorWithRetry() = runComposeUiTest {
        // No NFT client for the chain → repo.nfts throws → VM maps to a graceful Error (not a crash).
        val viewModel = vm(emptyMap())
        setContent { CryptasaTheme(ThemeMode.Light) { NftGridScreen(onBack = {}, viewModel = viewModel) } }
        onNodeWithTag(WalletTestTags.NFT_ERROR).assertIsDisplayed()
        onNodeWithTag(WalletTestTags.NFT_RETRY).assertIsDisplayed()
    }
}
