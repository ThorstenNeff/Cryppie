package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money

import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValuationTest {

    @Test
    fun parsesDecimalToFixedPoint() {
        assertEquals(254312000000L, Valuation.parseDecimalToScaled("2543.12", 8))
        assertEquals(99980000L, Valuation.parseDecimalToScaled("0.9998", 8))
        assertEquals(100000000L, Valuation.parseDecimalToScaled("1", 8))
        assertEquals(12345L, Valuation.parseDecimalToScaled("1.2345678", 4)) // 1.2345 (extra digits truncated)
        assertNull(Valuation.parseDecimalToScaled("-5", 8))
        assertNull(Valuation.parseDecimalToScaled("abc", 8))
    }

    @Test
    fun valuesSmallHolding() {
        // 2 USDC (6 decimals) @ $0.9998 → $1.9996 → 199 cents (truncating, never overstated).
        val cents = Valuation.valueCents(Quantity.of(2_000_000), decimals = 6, price = Money(99_980_000, 8, "USD"))
        assertEquals(199L, cents)
    }

    @Test
    fun valuesLargeHoldingBeyondLongProduct() {
        // 1000 ETH (1e21 wei) @ $2543.12 → product ~2.5e32 (256-bit), result $2,543,120 = 254_312_000 cents.
        var oneThousandEth = Quantity.of(1000)
        repeat(18) { oneThousandEth *= Quantity.of(10) } // 1000 × 10^18 wei
        val cents = Valuation.valueCents(oneThousandEth, decimals = 18, price = Money(254_312_000_000, 8, "USD"))
        assertEquals(254_312_000L, cents)
    }
}
