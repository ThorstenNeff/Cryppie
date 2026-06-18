package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostBasisEngineTest {

    // n whole 18-decimal tokens (e.g. ETH) in base units.
    private fun tokens(n: Long): Quantity {
        var v = Quantity.of(n)
        repeat(18) { v *= Quantity.of(10) }
        return v
    }

    // fiat price per whole token as a scale-8 Money (e.g. $100.00).
    private fun usd(dollars: Long) = Money(dollars * 100_000_000, 8, "USD")

    @Test
    fun fifoRealizesAgainstEarliestLotsFirst() {
        // buy 2 @ $100, buy 1 @ $200, sell 2 @ $300 → consume both of lot1 ($100):
        //   realized = (300−100)×2 = $400 = 40_000¢; remaining = 1 @ $200 → cost basis $200 = 20_000¢.
        val result = CostBasisEngine.fifo(
            decimals = 18,
            events = listOf(
                FifoEvent(received = true, amount = tokens(2), unitPrice = usd(100)),
                FifoEvent(received = true, amount = tokens(1), unitPrice = usd(200)),
                FifoEvent(received = false, amount = tokens(2), unitPrice = usd(300)),
            ),
        )
        assertEquals(tokens(1), result.currentAmount)
        assertEquals(20_000L, result.costBasisCents)
        assertEquals(40_000L, result.realizedPnlCents)
        assertTrue(ApproxReason.COST_BASIS_AMBIGUITY in result.reasons)
        assertTrue(ApproxReason.INCOMPLETE_TRANSFERS !in result.reasons) // fully tracked
    }

    @Test
    fun disposingMoreThanTrackedFlagsIncompleteHistory() {
        // buy 1 @ $100, sell 2 @ $300 → 1 tracked: (300−100)=$200; 1 untracked: proceeds $300, cost 0.
        val result = CostBasisEngine.fifo(
            decimals = 18,
            events = listOf(
                FifoEvent(received = true, amount = tokens(1), unitPrice = usd(100)),
                FifoEvent(received = false, amount = tokens(2), unitPrice = usd(300)),
            ),
        )
        assertEquals(Quantity.ZERO, result.currentAmount)
        assertEquals(0L, result.costBasisCents)
        assertEquals(20_000L + 30_000L, result.realizedPnlCents) // $200 + $300
        assertTrue(ApproxReason.INCOMPLETE_TRANSFERS in result.reasons)
        assertTrue(ApproxReason.WALLET_PREDATES_TRACKING in result.reasons)
    }

    @Test
    fun holdWithoutDisposalHasCostBasisAndZeroRealized() {
        val result = CostBasisEngine.fifo(
            decimals = 18,
            events = listOf(FifoEvent(received = true, amount = tokens(3), unitPrice = usd(150))),
        )
        assertEquals(tokens(3), result.currentAmount)
        assertEquals(45_000L, result.costBasisCents) // 3 × $150 = $450
        assertEquals(0L, result.realizedPnlCents)
    }
}
