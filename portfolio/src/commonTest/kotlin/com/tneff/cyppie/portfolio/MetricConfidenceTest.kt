package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.TokenPrice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricConfidenceTest {

    @Test
    fun robustMetricHasNoCaveat() {
        val m = Metric.robust(42)
        assertFalse(m.isApproximate)
        assertEquals(emptyList(), m.reasons)
        assertEquals(42, m.value)
    }

    @Test
    fun approximateCarriesItsReasons() {
        val m = Metric.approximate("≈1234", ApproxReason.COST_BASIS_AMBIGUITY, ApproxReason.INCOMPLETE_TRANSFERS)
        assertTrue(m.isApproximate)
        assertEquals(listOf(ApproxReason.COST_BASIS_AMBIGUITY, ApproxReason.INCOMPLETE_TRANSFERS), m.reasons)
    }

    @Test
    fun approximateDedupesReasons() {
        val m = Metric.approximate(1, ApproxReason.STALE_PRICES, ApproxReason.STALE_PRICES)
        assertEquals(listOf(ApproxReason.STALE_PRICES), m.reasons)
    }

    @Test
    fun mapKeepsTheConfidenceAndCaveat() {
        val mapped = Metric.approximate(10, ApproxReason.STALE_PRICES).map { it * 2 }
        assertEquals(20, mapped.value)
        assertTrue(mapped.isApproximate)
        assertEquals(listOf(ApproxReason.STALE_PRICES), mapped.reasons)
    }

    @Test
    fun priceStalenessUsesTheTtl() {
        val price = TokenPrice(Money(100, 2, "USD"), asOfEpochSeconds = 1_000)
        assertFalse(price.isStale(nowEpochSeconds = 1_030)) // 30s ≤ 60s TTL
        assertTrue(price.isStale(nowEpochSeconds = 1_100)) // 100s > 60s TTL
    }
}
