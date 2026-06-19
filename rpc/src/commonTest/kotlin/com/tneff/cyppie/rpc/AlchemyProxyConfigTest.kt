package com.tneff.cyppie.rpc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** KAN-112: the client routes Alchemy/RPC through the proxy base URL — no key in the client (ADR-0021). */
class AlchemyProxyConfigTest {

    private val config = AlchemyProxyConfig("http://localhost:8080/")

    @Test
    fun buildsProxyPathsAndTrimsTrailingSlash() {
        assertEquals("http://localhost:8080/alchemy/data/v1", config.dataBaseUrl())
        assertEquals("http://localhost:8080/alchemy/prices/v1", config.pricesBaseUrl())
        assertEquals("http://localhost:8080/alchemy/nft/v3/eth-mainnet", config.nftBaseUrl(1L))
        assertEquals("http://localhost:8080/alchemy/rpc/v2/base-mainnet", config.rpcUrl(8453L))
    }

    @Test
    fun urlsCarryNoApiKey() {
        // The whole point of the proxy: nothing key-bearing is ever assembled client-side.
        listOf(config.dataBaseUrl(), config.pricesBaseUrl(), config.nftBaseUrl(1L), config.rpcUrl(1L))
            .forEach { url -> assertEquals(true, "g.alchemy.com" !in url) }
    }

    @Test
    fun networkMapping() {
        assertEquals("eth-mainnet", AlchemyNetworks.of(1L))
        assertEquals("base-mainnet", AlchemyNetworks.of(8453L))
        assertNull(AlchemyNetworks.of(137L))
    }
}
