package com.tneff.cyppie.feature.market

import com.tneff.cyppie.market.Candle
import com.tneff.cyppie.market.CandleInterval
import com.tneff.cyppie.market.MarketAsset
import com.tneff.cyppie.market.MarketDataApi
import com.tneff.cyppie.market.PricePoint
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

private class FakeOverviewApi(
    val spots: Map<MarketAsset, SpotPrice> = emptyMap(),
    val throwOnSpot: Boolean = false,
    val historyByAsset: Map<MarketAsset, List<PricePoint>> = emptyMap(),
    val historyThrowsFor: Set<MarketAsset> = emptySet(),
) : MarketDataApi {
    override suspend fun candles(asset: MarketAsset, interval: CandleInterval, range: TimeRange): List<Candle> = emptyList()

    override suspend fun spotPrices(assets: List<MarketAsset>, vs: String): Map<MarketAsset, SpotPrice> {
        if (throwOnSpot) error("batch spot down")
        return spots.filterKeys { it in assets }
    }

    override suspend fun priceHistory(asset: MarketAsset, vs: String, interval: CandleInterval, range: TimeRange): List<PricePoint> {
        if (asset in historyThrowsFor) error("history down for $asset")
        return historyByAsset[asset].orEmpty()
    }
}

class MarketOverviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val eth = MarketAsset.Native(chainId = 1)
    private val usdc = MarketAsset.Erc20(chainId = 1, contract = com.tneff.cyppie.evm.EvmAddress.parse("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"))

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun pp(p: String) = PricePoint(epochSeconds = 1L, priceDecimal = p)

    @Test
    fun batchSpotPlusSparklines() = runTest(dispatcher) {
        val api = FakeOverviewApi(
            spots = mapOf(eth to SpotPrice("2000", "usd", "1.2"), usdc to SpotPrice("1", "usd", "0.0")),
            historyByAsset = mapOf(eth to listOf(pp("1990"), pp("2000")), usdc to listOf(pp("1"))),
        )
        val vm = MarketOverviewViewModel(
            watched = listOf(WatchedAsset(eth, "ETH"), WatchedAsset(usdc, "USDC")),
            vs = "usd", data = api, nowEpochSeconds = { 100_000L },
        )
        advanceUntilIdle()
        val rows = (vm.uiState as MarketOverviewUiState.Content).rows
        assertEquals(listOf("ETH", "USDC"), rows.map { it.label })
        assertEquals("2000", rows.first().spot?.priceDecimal)
        assertEquals(listOf(1990.0, 2000.0), rows.first().sparkline)
    }

    @Test
    fun sparklineFailureIsBestEffortPerAsset() = runTest(dispatcher) {
        val api = FakeOverviewApi(
            spots = mapOf(eth to SpotPrice("2000", "usd"), usdc to SpotPrice("1", "usd")),
            historyByAsset = mapOf(usdc to listOf(pp("1"))),
            historyThrowsFor = setOf(eth), // ETH history fails → empty sparkline, row still present
        )
        val vm = MarketOverviewViewModel(
            watched = listOf(WatchedAsset(eth, "ETH"), WatchedAsset(usdc, "USDC")),
            vs = "usd", data = api, nowEpochSeconds = { 1L },
        )
        advanceUntilIdle()
        val rows = (vm.uiState as MarketOverviewUiState.Content).rows
        assertTrue(rows.first { it.label == "ETH" }.sparkline.isEmpty())
        assertEquals("2000", rows.first { it.label == "ETH" }.spot?.priceDecimal)
        assertEquals(listOf(1.0), rows.first { it.label == "USDC" }.sparkline)
    }

    @Test
    fun missingSpotLeavesRowWithNullSpot() = runTest(dispatcher) {
        val api = FakeOverviewApi(spots = emptyMap()) // no price for the asset
        val vm = MarketOverviewViewModel(listOf(WatchedAsset(eth, "ETH")), "usd", api, nowEpochSeconds = { 1L })
        advanceUntilIdle()
        val rows = (vm.uiState as MarketOverviewUiState.Content).rows
        assertEquals(1, rows.size)
        assertEquals(null, rows.first().spot)
    }

    @Test
    fun batchSpotFailureIsError() = runTest(dispatcher) {
        val vm = MarketOverviewViewModel(listOf(WatchedAsset(eth, "ETH")), "usd", FakeOverviewApi(throwOnSpot = true), nowEpochSeconds = { 1L })
        advanceUntilIdle()
        assertTrue(vm.uiState is MarketOverviewUiState.Error)
    }
}
