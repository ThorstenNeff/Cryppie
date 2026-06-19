package com.tneff.cyppie

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** KAN — CoinGecko proxy: least-privilege allow-list, dedicated-key injection (no cross-vendor leak), client-key strip. */
class CoinGeckoProxyTest {

    // ---- unit: structural allow-list + key-injection builder ----

    @Test
    fun allowsTheSupportedEndpointShapes() {
        assertTrue(isAllowedCoinGeckoTail("simple/token_price/ethereum"))
        assertTrue(isAllowedCoinGeckoTail("simple/token_price/base"))
        assertTrue(isAllowedCoinGeckoTail("simple/price"))
        assertTrue(isAllowedCoinGeckoTail("coins/ethereum/contract/0x1111111111111111111111111111111111111111/market_chart"))
        assertTrue(isAllowedCoinGeckoTail("coins/base/contract/0xaBcdef0000000000000000000000000000000001/market_chart/range"))
        assertTrue(isAllowedCoinGeckoTail("coins/bitcoin/ohlc"))
    }

    @Test
    fun rejectsUnsupportedOrWrongShapedPaths() {
        assertFalse(isAllowedCoinGeckoTail("simple/token_price/solana")) // platform not supported
        assertFalse(isAllowedCoinGeckoTail("coins/ethereum/contract/not-an-address/market_chart")) // bad address
        assertFalse(isAllowedCoinGeckoTail("coins/ethereum/contract/0x1111111111111111111111111111111111111111/tickers")) // wrong leaf
        assertFalse(isAllowedCoinGeckoTail("coins/bitcoin/market_chart/range/extra")) // not an allowed shape
        assertFalse(isAllowedCoinGeckoTail("onchain/networks/eth/pools")) // arbitrary path
        assertFalse(isAllowedCoinGeckoTail("../../admin")) // traversal-ish → no shape match
        assertFalse(isAllowedCoinGeckoTail(""))
    }

    @Test
    fun injectsKeyAsServerSideQueryParam() {
        assertEquals(
            "https://pro-api.coingecko.com/api/v3/coins/bitcoin/ohlc?x_cg_pro_api_key=SECRET",
            CoinGeckoUpstream.url("SECRET", "coins/bitcoin/ohlc"),
        )
    }

    // ---- route-level: dedicated key, 503 fail-closed, client-key strip ----

    private class Captured { var url: String = "" }

    private fun mockClient(captured: Captured): HttpClient {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HttpClient(engine) { expectSuccess = false }
    }

    @Test
    fun routeUsesItsOwnCoinGeckoKeyNeverTheAlchemyKey() = testApplication {
        val captured = Captured()
        application { module(ProxyConfig(alchemyApiKey = "ALCHEMY_SECRET", coinGeckoApiKey = "CG_KEY"), mockClient(captured)) }

        val response = client.get("/coingecko/v3/coins/bitcoin/ohlc?vs_currency=usd&days=1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("x_cg_pro_api_key=CG_KEY" in captured.url, captured.url) // dedicated CoinGecko key…
        assertFalse("ALCHEMY_SECRET" in captured.url, captured.url) // …NEVER the Alchemy key (no cross-vendor leak)
        assertTrue("vs_currency=usd" in captured.url, captured.url) // client query still relayed
        assertTrue("days=1" in captured.url, captured.url)
    }

    @Test
    fun routeIs503WhenCoinGeckoKeyUnsetEvenWithAlchemyKeySet() = testApplication {
        val captured = Captured()
        // Alchemy key present, CoinGecko key absent → the route must NOT reach upstream on the Alchemy key.
        application { module(ProxyConfig(alchemyApiKey = "ALCHEMY_SECRET", coinGeckoApiKey = null), mockClient(captured)) }

        val response = client.get("/coingecko/v3/coins/bitcoin/ohlc?vs_currency=usd&days=1")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status) // 503, fail-closed — no leak
        assertEquals("", captured.url) // upstream never contacted
    }

    @Test
    fun stripsClientSuppliedKeyParamSoItCannotOverrideTheServerKey() = testApplication {
        val captured = Captured()
        application { module(ProxyConfig(alchemyApiKey = "A", coinGeckoApiKey = "CG_KEY"), mockClient(captured)) }

        // Attacker tries to slip in their own key param (any case).
        val response = client.get("/coingecko/v3/coins/bitcoin/ohlc?vs_currency=usd&x_cg_pro_api_key=ATTACKER&X_CG_DEMO_API_KEY=zz")

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse("ATTACKER" in captured.url, captured.url) // client key param stripped
        assertFalse("zz" in captured.url, captured.url) // case-insensitive strip
        assertEquals(1, Regex("x_cg_pro_api_key=").findAll(captured.url).count(), captured.url) // exactly the server key, no dup
        assertTrue("x_cg_pro_api_key=CG_KEY" in captured.url, captured.url)
    }

    @Test
    fun disallowedTailIs404AndNeverReachesUpstream() = testApplication {
        val captured = Captured()
        application { module(ProxyConfig(alchemyApiKey = "A", coinGeckoApiKey = "CG_KEY"), mockClient(captured)) }

        val response = client.get("/coingecko/v3/onchain/networks/eth/pools") // not an allowed shape

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("", captured.url) // upstream never contacted
    }
}
