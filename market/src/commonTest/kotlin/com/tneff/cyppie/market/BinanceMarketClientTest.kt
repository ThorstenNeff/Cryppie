package com.tneff.cyppie.market

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** KAN — Binance klines adapter: parse + interval mapping + request shape. */
class BinanceMarketClientTest {

    @Test
    fun parsesKlinesOhlcvMsToSeconds() = runTest {
        val body = """[[1700000000000,"42000.00","42100.00","41900.00","42050.00","12.50000000",1700003599999,"x",100,"x","x","0"]]"""
        val engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val candles = BinanceMarketClient("https://binance.test", HttpClient(engine)).klines("ETHUSDT", CandleInterval.H1)
        assertEquals(1, candles.size)
        assertEquals(1700000000L, candles[0].openEpochSeconds) // ms → s
        assertEquals("42000.00", candles[0].open)
        assertEquals("42100.00", candles[0].high)
        assertEquals("41900.00", candles[0].low)
        assertEquals("42050.00", candles[0].close)
        assertEquals("12.50000000", candles[0].volume)
    }

    @Test
    fun mapsIntervalAndSymbolIntoRequest() = runTest {
        var seen = ""
        val engine = MockEngine { req ->
            seen = req.url.toString()
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        BinanceMarketClient("https://binance.test", HttpClient(engine))
            .klines("BTCUSDT", CandleInterval.M5, startEpochSeconds = 1700000000, limit = 100)
        assertTrue("symbol=BTCUSDT" in seen, seen)
        assertTrue("interval=5m" in seen, seen) // CandleInterval → Binance string
        assertTrue("startTime=1700000000000" in seen, seen) // s → ms
        assertTrue("limit=100" in seen, seen)
    }

    @Test
    fun nonSuccessStatusThrowsUpstream() = runTest {
        val engine = MockEngine { respond("err", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/plain")) }
        assertFailsWith<MarketException.Upstream> {
            BinanceMarketClient("https://binance.test", HttpClient(engine)).klines("ETHUSDT", CandleInterval.D1)
        }
    }
}
