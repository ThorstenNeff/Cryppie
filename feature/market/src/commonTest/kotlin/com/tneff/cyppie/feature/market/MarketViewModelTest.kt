package com.tneff.cyppie.feature.market

import com.tneff.cyppie.market.Candle
import com.tneff.cyppie.market.CandleInterval
import com.tneff.cyppie.market.MarketAsset
import com.tneff.cyppie.market.MarketDataApi
import com.tneff.cyppie.market.SpotPrice
import com.tneff.cyppie.market.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeMarketDataApi(
    val candlesResult: List<Candle> = emptyList(),
    val throwOnCandles: Boolean = false,
    val stats: com.tneff.cyppie.market.MarketStats? = null,
) : MarketDataApi {
    val intervalsSeen = mutableListOf<CandleInterval>()

    override suspend fun candles(asset: MarketAsset, interval: CandleInterval, range: TimeRange): List<Candle> {
        if (throwOnCandles) error("upstream down")
        intervalsSeen += interval
        return candlesResult
    }

    override suspend fun spotPrices(assets: List<MarketAsset>, vs: String): Map<MarketAsset, SpotPrice> =
        assets.associateWith { SpotPrice(priceDecimal = "100", vs = vs, change24hPct = "1.5") }

    override suspend fun priceHistory(asset: MarketAsset, vs: String, interval: CandleInterval, range: TimeRange) =
        emptyList<com.tneff.cyppie.market.PricePoint>()

    override suspend fun marketStats(assets: List<MarketAsset>, vs: String): Map<MarketAsset, com.tneff.cyppie.market.MarketStats> =
        stats?.let { s -> assets.associateWith { s } } ?: emptyMap()
}

private fun candle(open: String, high: String, low: String, close: String) =
    Candle(openEpochSeconds = 1_000L, open = open, high = high, low = low, close = close)

class MarketViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val eth = MarketAsset.Native(chainId = 1)

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun rangeMapsToIntervalAndWindow() {
        assertEquals(CandleInterval.M15, MarketRange.DAY.toQuery(10_000L).first)
        assertEquals(CandleInterval.D1, MarketRange.YEAR.toQuery(10_000L).first)
        val (_, window) = MarketRange.DAY.toQuery(10_000L)
        assertEquals(10_000L, window.toEpochSeconds)
        assertEquals(10_000L - 86_400L, window.fromEpochSeconds)
    }

    @Test
    fun candleUpDownByClose() {
        assertTrue(CandleBar(0, 1.0, 2.0, 0.5, 1.5).up)
        assertTrue(!CandleBar(0, 2.0, 2.0, 0.5, 1.0).up)
    }

    @Test
    fun loadsCandlesAndDropsUnparseable() = runTest(dispatcher) {
        val api = FakeMarketDataApi(
            candlesResult = listOf(
                candle("10", "12", "9", "11"),
                candle("bad", "12", "9", "11"), // FR-6: any unparseable field → dropped
            ),
        )
        val vm = MarketViewModel(eth, "usd", api, nowEpochSeconds = { 10_000L })
        advanceUntilIdle()
        val content = vm.uiState as MarketUiState.Content
        assertEquals(1, content.candles.size)
        assertEquals(11.0, content.candles.first().close)
        assertEquals("1.5", content.spot?.change24hPct)
    }

    @Test
    fun emptyCandlesIsContentNotError() = runTest(dispatcher) {
        val vm = MarketViewModel(eth, "usd", FakeMarketDataApi(candlesResult = emptyList()), nowEpochSeconds = { 1L })
        advanceUntilIdle()
        val content = vm.uiState as MarketUiState.Content
        assertTrue(content.candles.isEmpty())
    }

    @Test
    fun upstreamFailureIsError() = runTest(dispatcher) {
        val vm = MarketViewModel(eth, "usd", FakeMarketDataApi(throwOnCandles = true), nowEpochSeconds = { 1L })
        advanceUntilIdle()
        assertTrue(vm.uiState is MarketUiState.Error)
    }

    @Test
    fun selectRangeReloadsWithNewInterval() = runTest(dispatcher) {
        val api = FakeMarketDataApi(candlesResult = listOf(candle("1", "1", "1", "1")))
        val vm = MarketViewModel(eth, "usd", api, nowEpochSeconds = { 1L })
        advanceUntilIdle()
        vm.selectRange(MarketRange.YEAR)
        advanceUntilIdle()
        assertEquals(MarketRange.YEAR, vm.range)
        assertTrue(api.intervalsSeen.contains(CandleInterval.D1)) // chart used the YEAR interval (metrics also fetch H1)
    }

    @Test
    fun metrics24hFromCandles() = runTest(dispatcher) {
        val api = FakeMarketDataApi(
            candlesResult = listOf(
                Candle(1L, "10", "12", "9", "11", volume = "100"),
                Candle(2L, "11", "15", "8", "14", volume = "50"),
            ),
        )
        val vm = MarketViewModel(eth, "usd", api, nowEpochSeconds = { 100_000L })
        advanceUntilIdle()
        // No marketStats configured → metrics null (the rows render "—"); chart still loads.
        assertEquals(null, (vm.uiState as MarketUiState.Content).metrics)
    }

    @Test
    fun metricsFromMarketStats() = runTest(dispatcher) {
        val api = FakeMarketDataApi(
            candlesResult = listOf(candle("1", "1", "1", "1")),
            stats = com.tneff.cyppie.market.MarketStats(marketCap = "1.2T", circulatingSupply = "120M", volume24h = "15B", vs = "usd"),
        )
        val vm = MarketViewModel(eth, "usd", api, nowEpochSeconds = { 1L })
        advanceUntilIdle()
        val metrics = (vm.uiState as MarketUiState.Content).metrics!!
        assertEquals("1.2T", metrics.marketCap)
        assertEquals("15B", metrics.volume24h)
        assertEquals("120M", metrics.circulatingSupply)
    }

    @Test
    fun freshnessStaleWhenSpotOld() = runTest(dispatcher) {
        // spot lastUpdated 10 min ago, now = 600s past → stale (> 300s threshold)
        val api = object : MarketDataApi {
            override suspend fun candles(asset: MarketAsset, interval: CandleInterval, range: TimeRange) =
                listOf(Candle(1L, "1", "1", "1", "1"))
            override suspend fun spotPrices(assets: List<MarketAsset>, vs: String) =
                assets.associateWith { SpotPrice("100", vs, "1.0", lastUpdatedEpochSeconds = 0L) }
            override suspend fun priceHistory(asset: MarketAsset, vs: String, interval: CandleInterval, range: TimeRange) =
                emptyList<com.tneff.cyppie.market.PricePoint>()
        }
        val vm = MarketViewModel(eth, "usd", api, nowEpochSeconds = { 600L })
        advanceUntilIdle()
        val freshness = (vm.uiState as MarketUiState.Content).freshness
        assertTrue(freshness is Freshness.Delayed && freshness.stale)
    }
}
