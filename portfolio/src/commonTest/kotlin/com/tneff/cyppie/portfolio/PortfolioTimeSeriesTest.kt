package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.FiatPricePoint

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PortfolioTimeSeriesTest {

    private val account = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val weth = PortfolioToken(1L, EvmAddress.parse("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"), "WETH", 18)

    private fun eth(n: Long): Quantity {
        var v = Quantity.of(n)
        repeat(18) { v *= Quantity.of(10) }
        return v
    }

    private fun usd(dollars: Long) = Money(dollars * 100_000_000, 8, "USD")
    private fun pp(epoch: Long, dollars: Long) = FiatPricePoint(epoch, usd(dollars))

    @Test
    fun valueSeriesReconstructsCumulativeHoldings() {
        val transfers = listOf(TimedAmount(100, received = true, eth(2)), TimedAmount(300, received = true, eth(1)))
        val history = listOf(pp(50, 100), pp(200, 150), pp(400, 200))
        val series = PortfolioTimeSeries.tokenValueSeries(18, transfers, history)

        assertEquals(3, series.size)
        assertEquals(Money(0, 2, "USD"), series[0].value) // t=50: 0 ETH
        assertEquals(Money(30_000, 2, "USD"), series[1].value) // t=200: 2 ETH × $150 = $300
        assertEquals(Money(60_000, 2, "USD"), series[2].value) // t=400: 3 ETH × $200 = $600
    }

    @Test
    fun valueSeriesHandlesDisposal() {
        val transfers = listOf(TimedAmount(100, received = true, eth(3)), TimedAmount(200, received = false, eth(1)))
        val series = PortfolioTimeSeries.tokenValueSeries(18, transfers, listOf(pp(300, 100)))
        assertEquals(Money(20_000, 2, "USD"), series[0].value) // 2 ETH × $100 = $200
    }

    @Test
    fun change24hIsHoldingsTimesPriceDeltaAndRobust() {
        val holdings = listOf(Holding(account, weth, eth(2)))
        val m = PortfolioTimeSeries.change24h(holdings, mapOf(weth to usd(200)), mapOf(weth to usd(150)), "USD")
        assertEquals(Money(10_000, 2, "USD"), m.value) // 2 × ($200 − $150) = $100
        assertFalse(m.isApproximate)
    }
}
