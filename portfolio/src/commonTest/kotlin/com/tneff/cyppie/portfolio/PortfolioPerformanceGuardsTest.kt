package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KAN-100 — [PortfolioPerformance] **determinism / edge** KATs (FR-6 float-free, FR-9 approximate),
 * complementing the Dev [PortfolioPerformanceTest]. Integer-only (Sharpe ×1000 via integer `isqrt`,
 * drawdown bps from the running peak) → reproducible, never float-drifting. Every metric is
 * `Approximate` with its caveat reasons. Synthetic series only (§5.3).
 */
class PortfolioPerformanceGuardsTest {

    private fun pt(epoch: Long, cents: Long) = ValuePoint(epoch, Money(cents, 2, "USD"))

    // ---- max drawdown: edge cases + exact bps from the running peak ----

    @Test
    fun maxDrawdownEdgeCasesAreZero() {
        assertEquals(0, PortfolioPerformance.maxDrawdownBps(emptyList()).value)
        assertEquals(0, PortfolioPerformance.maxDrawdownBps(listOf(pt(1, 10_000))).value)
        // Flat series → no drawdown.
        assertEquals(0, PortfolioPerformance.maxDrawdownBps(listOf(pt(1, 5_000), pt(2, 5_000), pt(3, 5_000))).value)
    }

    @Test
    fun maxDrawdownIsExactBpsOfRunningPeak() {
        // Peak 12_000 then trough 6_000 → (12000-6000)*10000/12000 = 5000 bps (50%). Later recovery
        // doesn't lower the recorded max.
        val series = listOf(pt(1, 10_000), pt(2, 12_000), pt(3, 6_000), pt(4, 11_000))
        assertEquals(5_000, PortfolioPerformance.maxDrawdownBps(series).value)
    }

    // ---- Sharpe ×1000: too-few → null, zero-variance → 0, deterministic integer value ----

    @Test
    fun sharpeNullForFewerThanThreePoints() {
        assertNull(PortfolioPerformance.sharpeMilli(emptyList()))
        assertNull(PortfolioPerformance.sharpeMilli(listOf(pt(1, 10_000), pt(2, 11_000)))) // 2 points
    }

    @Test
    fun sharpeZeroForFlatReturns() {
        // +10% each step → equal period returns → variance 0 → Sharpe 0 (no division by zero stddev).
        val steady = listOf(pt(1, 10_000), pt(2, 11_000), pt(3, 12_100), pt(4, 13_310))
        assertEquals(0L, PortfolioPerformance.sharpeMilli(steady)!!.value)
    }

    @Test
    fun sharpeIsDeterministicIntegerNoFloatDrift() {
        // returnsBps = [1000, 909, 833]; mean 914; variance 4660; isqrt → 68; sharpe = 914*1000/68 = 13441.
        val series = listOf(pt(1, 10_000), pt(2, 11_000), pt(3, 12_000), pt(4, 13_000))
        val sharpe = PortfolioPerformance.sharpeMilli(series)!!
        assertEquals(13_441L, sharpe.value)
        // Reproducible: same input → identical output (no float).
        assertEquals(sharpe.value, PortfolioPerformance.sharpeMilli(series)!!.value)
    }

    // ---- unrealized P&L: losses + alignment guards ----

    @Test
    fun unrealizedPnlHandlesLossAndGuardsAlignment() {
        // Loss: current 800 < cost 1000 → −200 cents.
        val loss = PortfolioPerformance.unrealizedPnl(Money(800, 2, "USD"), Money(1_000, 2, "USD"))
        assertEquals(Money(-200, 2, "USD"), loss.value)
        assertFailsWith<IllegalArgumentException> { PortfolioPerformance.unrealizedPnl(Money(1, 2, "USD"), Money(1, 4, "USD")) }
        assertFailsWith<IllegalArgumentException> { PortfolioPerformance.unrealizedPnl(Money(1, 2, "USD"), Money(1, 2, "EUR")) }
    }

    // ---- FR-9: every metric is Approximate with the right reasons ----

    @Test
    fun everyMetricIsApproximateWithItsReasons() {
        val series = listOf(pt(1, 10_000), pt(2, 12_000), pt(3, 6_000))
        assertTrue(PortfolioPerformance.maxDrawdownBps(series).isApproximate)
        assertEquals(
            listOf(ApproxReason.INCOMPLETE_TRANSFERS, ApproxReason.WALLET_PREDATES_TRACKING),
            PortfolioPerformance.maxDrawdownBps(series).reasons,
        )
        assertEquals(
            listOf(ApproxReason.INCOMPLETE_TRANSFERS, ApproxReason.COST_BASIS_AMBIGUITY),
            PortfolioPerformance.sharpeMilli(series)!!.reasons,
        )
        // P&L: COST_BASIS_AMBIGUITY always, plus any caller-supplied extra reasons.
        val pnl = PortfolioPerformance.unrealizedPnl(
            Money(100, 2, "USD"), Money(50, 2, "USD"), extraReasons = listOf(ApproxReason.STALE_PRICES),
        )
        assertEquals(listOf(ApproxReason.COST_BASIS_AMBIGUITY, ApproxReason.STALE_PRICES), pnl.reasons)
    }
}
