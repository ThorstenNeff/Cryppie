package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-102 — [CostBasisEngine] FIFO **multi-lot edge cases** (FR-6 float-free, FR-9 approximate),
 * complementing the Dev [CostBasisEngineTest]. Covers a disposal that spans several lots
 * (earliest-first, partial), the exact-lot boundary, a realized **loss** (negative), the precise
 * over-disposal amount (untracked = zero-cost + flags), and zero/empty inputs. (§5.3)
 *
 * Setup: decimals=0 + whole-dollar scale-8 prices → `valueCents(n, 0, $p) = n·p·100` (exact),
 * so every expected cent value below is hand-computable.
 */
class CostBasisEngineGuardsTest {

    private fun price(dollars: Long) = Money(dollars * 100_000_000L, 8, "USD") // $dollars at scale 8
    private fun recv(n: Long, p: Long) = FifoEvent(received = true, amount = Quantity.of(n), unitPrice = price(p))
    private fun disp(n: Long, p: Long) = FifoEvent(received = false, amount = Quantity.of(n), unitPrice = price(p))
    private fun fifo(events: List<FifoEvent>) = CostBasisEngine.fifo(decimals = 0, events = events)

    @Test
    fun disposalSpansMultipleLotsEarliestFirstWithPartialSecondLot() {
        // Acquire 10@$1, 10@$2, 10@$3; dispose 15@$5 → consumes lot1 fully + 5 of lot2.
        val r = fifo(listOf(recv(10, 1), recv(10, 2), recv(10, 3), disp(15, 5)))
        // realized = (proceeds−cost): lot1 (5000−1000) + lot2-part (2500−1000) = 4000 + 1500 = 5500.
        assertEquals(5_500L, r.realizedPnlCents)
        // remaining: 5@$2 + 10@$3 = 15 units; cost basis = 1000 + 3000 = 4000.
        assertEquals(Quantity.of(15), r.currentAmount)
        assertEquals(4_000L, r.costBasisCents)
        assertEquals(listOf(ApproxReason.COST_BASIS_AMBIGUITY), r.reasons) // fully tracked → no extra caveat
    }

    @Test
    fun exactLotBoundaryConsumesAndRemovesOnlyThatLot() {
        // Dispose exactly lot1's size → lot1 removed, lot2 untouched.
        val r = fifo(listOf(recv(10, 1), recv(10, 2), disp(10, 3)))
        assertEquals(2_000L, r.realizedPnlCents) // 3000 − 1000
        assertEquals(Quantity.of(10), r.currentAmount)
        assertEquals(2_000L, r.costBasisCents) // 10@$2
    }

    @Test
    fun disposalBelowCostRealizesNegativeLoss() {
        // Bought high ($5), sold low ($2) → realized loss.
        val r = fifo(listOf(recv(10, 5), disp(10, 2)))
        assertEquals(-3_000L, r.realizedPnlCents) // 2000 − 5000
        assertEquals(Quantity.ZERO, r.currentAmount)
        assertEquals(0L, r.costBasisCents)
    }

    @Test
    fun overDisposalRealizesUntrackedAtZeroCostAndFlagsIncompleteHistory() {
        // Only 10 tracked, dispose 15 → 10 against lot ($1), 5 untracked at zero cost.
        val r = fifo(listOf(recv(10, 1), disp(15, 5)))
        // realized = (5000−1000) tracked + 2500 untracked-zero-cost = 6500.
        assertEquals(6_500L, r.realizedPnlCents)
        assertEquals(Quantity.ZERO, r.currentAmount)
        assertEquals(0L, r.costBasisCents)
        assertEquals(
            listOf(ApproxReason.COST_BASIS_AMBIGUITY, ApproxReason.INCOMPLETE_TRANSFERS, ApproxReason.WALLET_PREDATES_TRACKING),
            r.reasons,
        )
    }

    @Test
    fun holdWithoutDisposalSumsCostBasisAndRealizesNothing() {
        val r = fifo(listOf(recv(10, 2), recv(5, 3)))
        assertEquals(Quantity.of(15), r.currentAmount)
        assertEquals(3_500L, r.costBasisCents) // 2000 + 1500
        assertEquals(0L, r.realizedPnlCents)
        assertEquals(listOf(ApproxReason.COST_BASIS_AMBIGUITY), r.reasons)
    }

    @Test
    fun emptyAndZeroAmountAcquisitionsYieldZero() {
        val empty = fifo(emptyList())
        assertEquals(Quantity.ZERO, empty.currentAmount)
        assertEquals(0L, empty.costBasisCents)
        assertEquals(0L, empty.realizedPnlCents)
        // A zero-amount acquisition adds no lot.
        val zero = fifo(listOf(recv(0, 5)))
        assertEquals(Quantity.ZERO, zero.currentAmount)
        assertEquals(0L, zero.costBasisCents)
    }
}
