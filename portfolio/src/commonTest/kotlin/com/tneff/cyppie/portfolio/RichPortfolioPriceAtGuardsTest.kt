package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.FiatPricePoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * KAN-102 — [RichPortfolio.priceAt] (block-time cost-basis pricing) **edge cases**, complementing the
 * Dev [RichPortfolioTest] (nearest-prior / before-first). The lookup is "nearest sample at-or-before
 * the epoch" and must be exact at the boundary, robust to unsorted input, and null on empty history.
 */
class RichPortfolioPriceAtGuardsTest {

    private fun usd(cents: Long) = Money(cents, 2, "USD")
    private fun pp(epoch: Long, cents: Long) = FiatPricePoint(epoch, usd(cents))

    @Test
    fun emptyHistoryIsNull() {
        assertNull(RichPortfolio.priceAt(emptyList(), epoch = 1_000))
    }

    @Test
    fun boundaryIsAtOrBeforeNotAfter() {
        val history = listOf(pp(100, 1_00), pp(200, 2_00))
        assertEquals(usd(1_00), RichPortfolio.priceAt(history, 199)) // just before the 200 sample → 100's price
        assertEquals(usd(2_00), RichPortfolio.priceAt(history, 200)) // exactly at → that sample
        assertEquals(usd(2_00), RichPortfolio.priceAt(history, 9_999)) // after last → last (nearest prior)
    }

    @Test
    fun unsortedHistoryIsSortedInternally() {
        // Same data shuffled → identical result (function sorts by epoch).
        val shuffled = listOf(pp(300, 3_00), pp(100, 1_00), pp(200, 2_00))
        assertEquals(usd(2_00), RichPortfolio.priceAt(shuffled, 250))
        assertEquals(usd(1_00), RichPortfolio.priceAt(shuffled, 50)) // before first → first
    }
}
