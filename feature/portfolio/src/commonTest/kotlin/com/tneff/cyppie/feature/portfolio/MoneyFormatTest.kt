package com.tneff.cyppie.feature.portfolio

import com.tneff.cyppie.portfolio.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/** PF-1 presentation formatting — float-free, deterministic (FR-6). */
class MoneyFormatTest {

    @Test
    fun groupsThousandsWithSymbol() {
        assertEquals("$1,234.56", Money(123_456, 2, "USD").formatted())
        assertEquals("€1,000,000.00", Money(100_000_000, 2, "EUR").formatted())
        assertEquals("£12.00", Money(1_200, 2, "GBP").formatted())
    }

    @Test
    fun padsSubUnitAndKeepsLeadingZero() {
        assertEquals("$0.05", Money(5, 2, "USD").formatted())
        assertEquals("$0.00", Money(0, 2, "USD").formatted())
    }

    @Test
    fun unknownCurrencyUsesIsoCodePrefix() {
        assertEquals("CHF 1,234.56", Money(123_456, 2, "CHF").formatted())
    }

    @Test
    fun negativeKeepsSignBeforeSymbol() {
        assertEquals("-$1,234.56", Money(-123_456, 2, "USD").formatted())
    }

    @Test
    fun signedShowsPlusForGainAndMinusForLoss() {
        assertEquals("+$1,234.56", Money(123_456, 2, "USD").formattedSigned())
        assertEquals("-$0.50", Money(-50, 2, "USD").formattedSigned())
        assertEquals("$0.00", Money(0, 2, "USD").formattedSigned()) // zero carries no sign
    }

    @Test
    fun bpsRenderAsPercentTwoDecimals() {
        assertEquals("25.00%", formatBps(2_500))
        assertEquals("100.00%", formatBps(10_000))
        assertEquals("8.33%", formatBps(833))
        assertEquals("0.05%", formatBps(5))
    }
}
