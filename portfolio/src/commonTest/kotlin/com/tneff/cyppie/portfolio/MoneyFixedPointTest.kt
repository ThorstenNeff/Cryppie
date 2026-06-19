package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.TokenPrice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * KAN-96 — [Money] fixed-point **determinism** (FR-6, correctness-critical) + [TokenPrice.isStale]
 * boundary + [Metric] over Money — complementing the Dev [MetricConfidenceTest]. Money is
 * integer-based (`minorUnits / 10^scale`), so valuation is exact + reproducible — never float.
 *
 * NOTE: this `:portfolio` slice is the model **foundation** — Money has no arithmetic yet, so the
 * rounding / sum-overflow KATs belong with the valuation slice (rawBalance × price → Money). Flagged.
 */
class MoneyFixedPointTest {

    @Test
    fun exactFixedPointRepresentationAcrossScales() {
        // $1.23 = 123 cents (scale 2); deterministic integer, no float.
        val usd = Money(123, 2, "USD")
        assertEquals(123, usd.minorUnits)
        assertEquals(2, usd.scale)
        // 1.00000001 at scale 8 — sub-cent precision held exactly.
        assertEquals(100_000_001, Money(100_000_001, 8, "USD").minorUnits)
        // scale 0 = whole units.
        assertEquals(42, Money(42, 0, "JPY").minorUnits)
    }

    @Test
    fun largeValuesHoldWithoutFloatDriftOrOverflow() {
        // A whole-cent value near Long.MAX (~$92.2 quadrillion) is exact — a Double would lose precision here.
        val huge = Money(Long.MAX_VALUE, 2, "USD")
        assertEquals(Long.MAX_VALUE, huge.minorUnits)
        // Two adjacent large values are distinct (no float collapsing them to the same Double).
        assertNotEquals(Money(Long.MAX_VALUE, 2, "USD"), Money(Long.MAX_VALUE - 1, 2, "USD"))
    }

    @Test
    fun equalityIsStructuralByUnitsScaleAndCurrency() {
        assertEquals(Money(100, 2, "USD"), Money(100, 2, "USD"))
        assertNotEquals(Money(100, 2, "USD"), Money(100, 2, "EUR")) // currency matters
        // Same real value ($1.00) at different scales is NOT structurally equal → comparison/sum needs
        // scale normalization (not in the foundation yet; flagged for the valuation slice).
        assertNotEquals(Money(100, 2, "USD"), Money(10_000, 4, "USD"))
    }

    @Test
    fun zeroIsCanonical() {
        assertEquals(Money(0, 2, "USD"), Money.zero("USD"))
        assertEquals(Money(0, 8, "ETH"), Money.zero("ETH", scale = 8))
    }

    @Test
    fun priceStalenessTtlBoundaryIsExclusive() {
        // isStale uses `now - asOf > maxAge` → exactly TTL is NOT stale; one second past is.
        val price = TokenPrice(Money(100, 2, "USD"), asOfEpochSeconds = 1_000)
        assertFalse(price.isStale(nowEpochSeconds = 1_060)) // exactly 60s = TTL → fresh
        assertTrue(price.isStale(nowEpochSeconds = 1_061))  // 61s > 60s → stale
        assertFalse(price.isStale(nowEpochSeconds = 1_059)) // 59s → fresh
        // Custom window (e.g. balance TTL 30s).
        assertTrue(price.isStale(nowEpochSeconds = 1_031, maxAgeSeconds = TokenPrice.BALANCE_TTL_SECONDS))
    }

    @Test
    fun metricOverMoneyCarriesConfidence() {
        val total = Money(1_234_56, 2, "USD")
        assertFalse(Metric.robust(total).isApproximate)
        val approx = Metric.approximate(total, ApproxReason.STALE_PRICES, ApproxReason.STALE_PRICES)
        assertTrue(approx.isApproximate)
        assertEquals(listOf(ApproxReason.STALE_PRICES), approx.reasons) // deduped
        assertEquals(total, approx.value)
    }
}
