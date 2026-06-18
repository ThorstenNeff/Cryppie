package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * KAN-98 — valuation **rounding / overflow / scale-alignment guards** (FR-6, correctness-critical),
 * complementing the Dev [ValuationTest] / [PortfolioValuatorTest]. Fixed-point fiat must **truncate
 * (never overstate)**, **saturate on overflow** (no wrap → never wrong-but-plausible), and **never
 * silently mix scales/currencies** when summing. Big-int-safe (wei × price > 64 bits). (§5.3)
 */
class ValuationGuardsTest {

    // ---- parse: truncates the fraction, never rounds up; rejects bad input ----

    @Test
    fun parseTruncatesFractionAndNeverRoundsUp() {
        // 9 fraction digits at scale 8 → the 9th is dropped, NOT rounded → 1.99999999 (never 2.0).
        assertEquals(199_999_999L, Valuation.parseDecimalToScaled("1.999999999", 8))
        assertEquals(250L, Valuation.parseDecimalToScaled("2.5", 2))
        assertEquals(0L, Valuation.parseDecimalToScaled("0.009", 2)) // sub-cent → floored to 0
        assertEquals(300L, Valuation.parseDecimalToScaled("3", 2))   // no fraction → padded
    }

    @Test
    fun parseRejectsNegativeAndNonNumeric() {
        assertNull(Valuation.parseDecimalToScaled("-1.00", 2))
        assertNull(Valuation.parseDecimalToScaled("abc", 2))
        assertNull(Valuation.parseDecimalToScaled("", 2))
    }

    // ---- valueCents: exact for normal sizes, truncates sub-cent, saturates on overflow ----

    @Test
    fun valueCentsIsExactAndTruncatesSubCent() {
        // 1.5 ETH × $2000 = $3000 = 300_000 cents.
        val onePointFiveEth = Quantity.of(1_500_000_000_000_000_000L)
        val price2000 = Money(2000_00000000, Valuation.PRICE_SCALE, "USD") // $2000 at scale 8
        assertEquals(300_000L, Valuation.valueCents(onePointFiveEth, decimals = 18, price = price2000))
        // Zero balance / non-positive price → 0.
        assertEquals(0L, Valuation.valueCents(Quantity.ZERO, 18, price2000))
        assertEquals(0L, Valuation.valueCents(onePointFiveEth, 18, Money(0, 8, "USD")))
    }

    @Test
    fun valueCentsSaturatesToLongMaxOnOverflowNeverWraps() {
        // An absurd whale balance (~160-bit) × $1 overflows Long cents → saturates to Long.MAX_VALUE
        // (a wrap would show a plausible-but-wrong negative/small number — forbidden, FR-6).
        val whale = Quantity.ofHex("0x" + "ff".repeat(20)) // ~1.46e48
        val price1 = Money(1_00000000, Valuation.PRICE_SCALE, "USD") // $1
        assertEquals(Long.MAX_VALUE, Valuation.valueCents(whale, decimals = 18, price = price1))
    }

    // ---- Money sum: scale + currency must align; atScale normalizes deterministically ----

    @Test
    fun moneyPlusGuardsScaleAndCurrency() {
        assertEquals(Money(150, 2, "USD"), Money(100, 2, "USD") + Money(50, 2, "USD"))
        assertFailsWith<IllegalArgumentException> { Money(100, 2, "USD") + Money(100, 4, "USD") } // scale
        assertFailsWith<IllegalArgumentException> { Money(100, 2, "USD") + Money(100, 2, "EUR") } // currency
    }

    @Test
    fun atScaleNormalizesUpExactAndDownTruncating() {
        assertEquals(Money(10_000, 4, "USD"), Money(100, 2, "USD").atScale(4))   // up: exact (×100)
        assertEquals(Money(1, 0, "USD"), Money(199, 2, "USD").atScale(0))        // down: 199c → $1 (truncates)
        // After normalizing, summing different-scale values works.
        assertEquals(Money(10_050, 4, "USD"), Money(100, 2, "USD").atScale(4) + Money(50, 4, "USD"))
    }

    // ---- divPow10: big-int multi-pass (exp > 9) stays exact ----

    @Test
    fun divPow10MultiPassIsExact() {
        // 10^30 / 10^12 = 10^18 — exp>9 forces the multi-pass path; must be exact, no Long overflow.
        val tenPow15 = Quantity.of(1_000_000_000_000_000L)
        val tenPow30 = tenPow15 * tenPow15 // big-int, > 64 bits
        assertEquals(Quantity.of(1_000_000_000_000_000_000L), tenPow30.divPow10(12))
        // Dividing out all 30 digits → 1; one more → 0 (exact integer floor).
        assertEquals(Quantity.of(1L), tenPow30.divPow10(30))
        assertEquals(Quantity.ZERO, tenPow30.divPow10(31))
    }
}
