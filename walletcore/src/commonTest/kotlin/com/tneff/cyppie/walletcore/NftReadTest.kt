package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.rpc.NftItem
import com.tneff.cyppie.rpc.NftPage
import com.tneff.cyppie.rpc.NftReadClient
import com.tneff.cyppie.rpc.NftType
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeNftClient(private val page: NftPage) : NftReadClient {
    var lastPageKey: String? = "UNSET"
    override val degraded = page.degraded
    override suspend fun nftsForOwner(owner: EvmAddress, pageKey: String?, pageSize: Int, excludeSpam: Boolean): NftPage {
        lastPageKey = pageKey
        return page
    }
}

class NftReadTest {

    private val accountManager = AccountManager(
        EvmKeyManager(SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed())),
    )

    private fun repo(nft: Map<Long, NftReadClient>) = WalletRepository(accountManager, emptyMap(), nft)

    @Test
    fun nftsReturnsClientPageWithDegraded() = runTest {
        val item = NftItem(
            chainId = 1L,
            contract = EvmAddress.parse("0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D"),
            tokenId = "1", type = NftType.ERC721, name = "Ape", collectionName = "BAYC",
            imageUrl = null, balance = null, isSpam = false,
        )
        val fake = FakeNftClient(NftPage(listOf(item), nextPageKey = "P2", totalCount = 1, degraded = true))
        val page = repo(mapOf(EvmChain.ETHEREUM.chainId to fake)).nfts(0, EvmChain.ETHEREUM)
        assertEquals(1, page.items.size)
        assertEquals("P2", page.nextPageKey)
        assertTrue(page.degraded)
    }

    @Test
    fun nftsPassesPageKeyThrough() = runTest {
        val fake = FakeNftClient(NftPage(emptyList(), null, 0, false))
        repo(mapOf(EvmChain.ETHEREUM.chainId to fake)).nfts(0, EvmChain.ETHEREUM, pageKey = "CURSOR")
        assertEquals("CURSOR", fake.lastPageKey)
    }

    @Test
    fun nftsUnconfiguredChainThrows() = runTest {
        assertFailsWith<IllegalArgumentException> { repo(emptyMap()).nfts(0, EvmChain.BASE) }
    }
}
