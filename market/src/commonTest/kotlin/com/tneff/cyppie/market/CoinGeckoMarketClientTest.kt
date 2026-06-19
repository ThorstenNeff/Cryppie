package com.tneff.cyppie.market

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** KAN — CoinGecko adapter parse correctness (precision-preserving, ms→s, via the proxy base URL). */
class CoinGeckoMarketClientTest {

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK): CoinGeckoMarketClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return CoinGeckoMarketClient("https://proxy.test/coingecko/v3", HttpClient(engine))
    }

    @Test
    fun parsesTokenSpotPricesWithChangeAndStamp() = runTest {
        val c = client("""{"0xabc":{"usd":2451.778899112233,"usd_24h_change":-1.25,"last_updated_at":1700000000}}""")
        val out = c.tokenSpotPrices("ethereum", listOf("0xabc"), "usd")
        val p = out.getValue("0xabc")
        assertEquals("2451.778899112233", p.priceDecimal) // full precision preserved (no Double round-trip)
        assertEquals("usd", p.vs)
        assertEquals("-1.25", p.change24hPct)
        assertEquals(1700000000L, p.lastUpdatedEpochSeconds)
    }

    @Test
    fun parsesContractMarketChartRangeMsToSeconds() = runTest {
        val c = client("""{"prices":[[1711843200000,69702.3087473573],[1711929600000,71246.95]]}""")
        val pts = c.contractMarketChartRange("base", "0xdef", "usd", 1711843200, 1711929600)
        assertEquals(2, pts.size)
        assertEquals(1711843200L, pts[0].epochSeconds) // ms → s
        assertEquals("69702.3087473573", pts[0].priceDecimal)
        assertEquals("71246.95", pts[1].priceDecimal)
    }

    @Test
    fun parsesOhlcCandlesWithoutVolume() = runTest {
        val c = client("""[[1709395200000,61942,62211,61721,61845],[1709409600000,61828,62139,61726,62139]]""")
        val candles = c.coinOhlc("ethereum", "usd", "1")
        assertEquals(2, candles.size)
        assertEquals(1709395200L, candles[0].openEpochSeconds)
        assertEquals("61942", candles[0].open)
        assertEquals("62211", candles[0].high)
        assertEquals("61721", candles[0].low)
        assertEquals("61845", candles[0].close)
        assertNull(candles[0].volume) // /ohlc carries no volume
    }

    @Test
    fun nonSuccessStatusThrowsUpstream() = runTest {
        val c = client("rate limited", HttpStatusCode.TooManyRequests)
        assertFailsWith<MarketException.Upstream> { c.coinOhlc("ethereum", "usd", "1") }
    }

    @Test
    fun emptyContractListSkipsRequest() = runTest {
        val c = client("{}")
        assertEquals(emptyMap(), c.tokenSpotPrices("ethereum", emptyList(), "usd"))
    }
}
