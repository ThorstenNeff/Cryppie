package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioPerformanceTest {

    private fun pt(epoch: Long, cents: Long) = ValuePoint(epoch, Money(cents, 2, "USD"))

    @Test
    fun unrealizedPnlIsValueMinusCostBasisAndApproximate() {
        val m = PortfolioPerformance.unrealizedPnl(Money(150_000, 2, "USD"), Money(100_000, 2, "USD"))
        assertEquals(Money(50_000, 2, "USD"), m.value) // +$500
        assertTrue(m.isApproximate)
        assertTrue(ApproxReason.COST_BASIS_AMBIGUITY in m.reasons)
    }

    @Test
    fun maxDrawdownIsPeakToTrough() {
        // peak 1000, deepest trough 400 → (1000-400)/1000 = 60% = 6000 bps
        val series = listOf(pt(1, 1000), pt(2, 1000), pt(3, 600), pt(4, 800), pt(5, 400))
        val m = PortfolioPerformance.maxDrawdownBps(series)
        assertEquals(6000, m.value)
        assertTrue(m.isApproximate)
    }

    @Test
    fun risingSeriesHasZeroDrawdown() {
        assertEquals(0, PortfolioPerformance.maxDrawdownBps(listOf(pt(1, 100), pt(2, 200), pt(3, 300))).value)
    }

    @Test
    fun sharpePositiveForSteadyGains() {
        val series = listOf(pt(1, 100), pt(2, 110), pt(3, 121), pt(4, 133)) // ~+10% steps, low variance
        val m = PortfolioPerformance.sharpeMilli(series)
        assertNotNull(m)
        assertTrue(m.value > 0)
        assertTrue(m.isApproximate)
    }

    @Test
    fun sharpeNullForTooFewPoints() {
        assertNull(PortfolioPerformance.sharpeMilli(listOf(pt(1, 100), pt(2, 110))))
    }
}
