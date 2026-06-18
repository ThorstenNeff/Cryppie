package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AlchemyDataPriceClientTest {

    private val holder = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val usdc = EvmAddress.parse("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(engine: MockEngine) = HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } }

    @Test
    fun parsesMultiChainTokenHoldings() = runTest {
        val engine = MockEngine {
            ok(
                """{"data":{"tokens":[
                  {"network":"eth-mainnet","tokenAddress":"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48","tokenBalance":"0x1e8480","tokenMetadata":{"symbol":"USDC","decimals":6}},
                  {"network":"base-mainnet","tokenAddress":"0x4200000000000000000000000000000000000006","tokenBalance":"0x0de0b6b3a7640000","tokenMetadata":{"symbol":"WETH","decimals":18}}
                ]}}""",
            )
        }
        val holdings = AlchemyDataClient("https://data", client(engine)).tokenHoldings(listOf(holder), listOf(1L, 8453L))
        assertEquals(2, holdings.size)
        assertEquals(1L, holdings[0].chainId)
        assertEquals("USDC", holdings[0].symbol)
        assertEquals(6, holdings[0].decimals)
        assertEquals(Quantity.of(2_000_000), holdings[0].balance) // 2 USDC
        assertEquals(8453L, holdings[1].chainId)
        assertEquals(holder, holdings[1].holder)
    }

    @Test
    fun parsesPricesByAddress() = runTest {
        val engine = MockEngine {
            ok(
                """{"data":[
                  {"network":"eth-mainnet","address":"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                   "prices":[{"currency":"usd","value":"0.9998","lastUpdatedAt":"2026-06-18T00:00:00Z"}]}
                ]}""",
            )
        }
        val prices = AlchemyPriceClient("https://prices", client(engine)).pricesByAddress(listOf(1L to usdc), "usd")
        assertEquals(1, prices.size)
        assertEquals("0.9998", prices[0].priceDecimal)
        assertEquals("usd", prices[0].currency)
        assertEquals(1L, prices[0].chainId)
    }

    @Test
    fun priceErrorEntriesAreSkipped() = runTest {
        val engine = MockEngine {
            ok("""{"data":[{"network":"eth-mainnet","address":"0xa0b8...","prices":[],"error":"not found"}]}""")
        }
        val prices = AlchemyPriceClient("https://prices", client(engine)).pricesByAddress(listOf(1L to usdc), "usd")
        assertEquals(emptyList(), prices)
    }

    @Test
    fun httpErrorIsAllProvidersFailed() = runTest {
        val engine = MockEngine { respond("upstream", HttpStatusCode.InternalServerError) }
        assertFailsWith<RpcException.AllProvidersFailed> {
            AlchemyDataClient("https://data", client(engine)).tokenHoldings(listOf(holder), listOf(1L))
        }
    }
}
