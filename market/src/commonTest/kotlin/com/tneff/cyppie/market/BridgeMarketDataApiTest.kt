package com.tneff.cyppie.market

import com.tneff.cyppie.evm.EvmAddress
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Routing / failover / fiat-gating / batching / FR-6-conversion for [BridgeMarketDataApi] (KAN-132).
 * Two recording MockEngines (CoinGecko + Binance) let each test assert **which** upstream a method hit.
 */
class BridgeMarketDataApiTest {

    private val ethNative = MarketAsset.Native(1L)
    private val usdc = MarketAsset.Erc20(1L, EvmAddress.parse("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"))
    private val dai = MarketAsset.Erc20(1L, EvmAddress.parse("0x6B175474E89094C44Da98b954EedeAC495271d0F"))
    private val weth = MarketAsset.Erc20(1L, EvmAddress.parse("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))
    private val unknown = MarketAsset.Erc20(1L, EvmAddress.parse("0x1111111111111111111111111111111111111111"))

    private class Calls {
        val cg = mutableListOf<Url>()
        val bin = mutableListOf<Url>()
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun bridge(
        calls: Calls = Calls(),
        cg: (Url) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to "{}" },
        bin: (Url) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to "[]" },
        clock: () -> Long = { 1_000L },
    ): BridgeMarketDataApi {
        val cgEngine = MockEngine { req -> calls.cg.add(req.url); val (s, b) = cg(req.url); respond(b, s, jsonHeaders) }
        val binEngine = MockEngine { req -> calls.bin.add(req.url); val (s, b) = bin(req.url); respond(b, s, jsonHeaders) }
        return BridgeMarketDataApi(
            coinGecko = CoinGeckoMarketClient("https://cg.test/coingecko/v3", HttpClient(cgEngine)),
            binance = BinanceMarketClient("https://binance.test", HttpClient(binEngine)),
            catalog = DefaultMarketAssetCatalog,
            clockEpochSeconds = clock,
        )
    }

    private val binanceCandles = """[[1700000000000,"2500.0","2510.0","2490.0","2505.5","1.5",1700000059999]]"""
    private val coinGeckoOhlc = """[[1700000000000,2400,2420,2390,2410]]"""

    // ---- candles routing -----------------------------------------------------------------------

    @Test
    fun candlesRouteNativeToBinance() = runTest {
        val calls = Calls()
        val api = bridge(calls, bin = { HttpStatusCode.OK to binanceCandles })
        val out = api.candles(ethNative, CandleInterval.H1, TimeRange(1_700_000_000, 1_700_086_400))
        assertEquals(1, out.size)
        assertEquals("2505.5", out[0].close)
        assertTrue(calls.bin.any { it.encodedPath.endsWith("/api/v3/klines") })
        assertTrue(calls.cg.isEmpty()) // Binance served — CoinGecko never touched
    }

    @Test
    fun candlesRouteWethToCoinGeckoOhlc() = runTest {
        val calls = Calls()
        val api = bridge(calls, cg = { HttpStatusCode.OK to coinGeckoOhlc })
        val out = api.candles(weth, CandleInterval.H1, TimeRange(1_700_000_000, 1_700_086_400))
        assertEquals("2410", out[0].close)
        assertTrue(calls.cg.any { it.encodedPath.contains("/coins/weth/ohlc") }) // WETH → coin-id /ohlc, not Binance
        assertTrue(calls.bin.isEmpty())
    }

    @Test
    fun candlesUnsupportedWhenNoSource() = runTest {
        val api = bridge()
        assertFailsWith<MarketException.Unsupported> {
            api.candles(unknown, CandleInterval.H1, TimeRange(1_700_000_000, 1_700_086_400))
        }
    }

    @Test
    fun candlesFailoverBinanceToCoinGecko() = runTest {
        val calls = Calls()
        val api = bridge(
            calls,
            cg = { HttpStatusCode.OK to coinGeckoOhlc },
            bin = { HttpStatusCode.InternalServerError to "boom" },
        )
        val out = api.candles(ethNative, CandleInterval.H1, TimeRange(1_700_000_000, 1_700_086_400))
        assertEquals("2410", out[0].close) // Binance failed → CoinGecko /ohlc served
        assertTrue(calls.bin.isNotEmpty() && calls.cg.isNotEmpty())
    }

    // ---- spot routing / batching / fallback / fiat ---------------------------------------------

    @Test
    fun spotBatchesErc20PerPlatformInOneCall() = runTest {
        val calls = Calls()
        val body = """{
          "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48":{"usd":1.0,"last_updated_at":1700000000},
          "0x6b175474e89094c44da98b954eedeac495271d0f":{"usd":0.9998,"last_updated_at":1700000000}
        }"""
        val api = bridge(calls, cg = { HttpStatusCode.OK to body })
        val out = api.spotPrices(listOf(usdc, dai), "usd")
        assertEquals("1.0", out.getValue(usdc).priceDecimal)
        assertEquals("0.9998", out.getValue(dai).priceDecimal)
        // One batched request to the platform endpoint, not one per token.
        assertEquals(1, calls.cg.count { it.encodedPath.contains("/simple/token_price/ethereum") })
    }

    @Test
    fun spotNativeByCoinIdConvertsToMoneyOnce() = runTest {
        val body = """{"ethereum":{"usd":2451.778899,"usd_24h_change":1.5,"last_updated_at":1700000000}}"""
        val api = bridge(cg = { url ->
            if (url.encodedPath.endsWith("/simple/price")) HttpStatusCode.OK to body else HttpStatusCode.OK to "{}"
        })
        val prices = api.currentPrices(listOf(ethNative), "usd")
        val tp = prices.getValue(ethNative)
        assertEquals(Money(245_177_889_900L, PRICE_SCALE, "usd"), tp.price) // string → fixed-point, once
        assertEquals(1_700_000_000L, tp.asOfEpochSeconds) // upstream stamp preferred over the clock
        assertEquals(150, tp.change24hBps) // 1.5% → 150 bps
    }

    @Test
    fun spotFallbackToBinanceForUnresolvedUsdMajor() = runTest {
        val calls = Calls()
        // CoinGecko returns nothing for the native coin → USD fallback to Binance last-close.
        val api = bridge(
            calls,
            cg = { HttpStatusCode.OK to "{}" },
            bin = { HttpStatusCode.OK to binanceCandles },
        )
        val out = api.spotPrices(listOf(ethNative), "usd")
        assertEquals("2505.5", out.getValue(ethNative).priceDecimal) // Binance kline close
        assertTrue(calls.bin.any { it.parameters["limit"] == "1" }) // latest-close probe
    }

    @Test
    fun nonUsdFiatNeverFallsBackToBinance() = runTest {
        val calls = Calls()
        val api = bridge(
            calls,
            cg = { HttpStatusCode.OK to "{}" }, // CoinGecko has no EUR price for it
            bin = { HttpStatusCode.OK to binanceCandles },
        )
        val out = api.spotPrices(listOf(ethNative), "eur")
        assertTrue(out.isEmpty()) // degrade — no stale, no USD-priced Binance leakage into a EUR request
        assertTrue(calls.bin.isEmpty())
    }

    // ---- history -------------------------------------------------------------------------------

    @Test
    fun priceHistoryByContractConvertsToFiatPoints() = runTest {
        val body = """{"prices":[[1700000000000,1.0001],[1700003600000,0.9999]]}"""
        val api = bridge(cg = { HttpStatusCode.OK to body })
        val pts = api.priceHistory(usdc, "usd", 1_700_000_000, 1_700_086_400, CandleInterval.H1.seconds)
        assertEquals(2, pts.size)
        assertEquals(1_700_000_000L, pts[0].epochSeconds)
        assertEquals(Money(100_010_000L, PRICE_SCALE, "usd"), pts[0].price) // "1.0001" → 1.0001×10^8
    }

    // ---- market stats --------------------------------------------------------------------------

    @Test
    fun marketStatsByCoinIdInOneCall() = runTest {
        val calls = Calls()
        val body = """[
          {"id":"ethereum","market_cap":300000000000,"circulating_supply":120000000.0,"total_volume":15000000000},
          {"id":"usd-coin","market_cap":32000000000,"circulating_supply":32000000000.0,"total_volume":5000000000}
        ]"""
        val api = bridge(calls, cg = { HttpStatusCode.OK to body })
        val out = api.marketStats(listOf(ethNative, usdc), "usd")
        assertEquals("300000000000", out.getValue(ethNative).marketCap)
        assertEquals("32000000000", out.getValue(usdc).marketCap)
        assertEquals(1, calls.cg.count { it.encodedPath.endsWith("/coins/markets") }) // one batched call
    }
}
