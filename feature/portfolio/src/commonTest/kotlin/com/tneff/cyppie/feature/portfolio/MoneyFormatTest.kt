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

    @Test
    fun negativeBpsKeepSignEvenBelowOnePercent() {
        // L1 regression: a small negative (whole part 0) must still show the minus.
        assertEquals("-0.05%", formatBps(-5))
        assertEquals("-0.50%", formatBps(-50))
        assertEquals("-12.34%", formatBps(-1_234))
    }

    // ---- KAN-116: locale-aware format classes (float-free) ----

    private val nbsp = " "

    @Test
    fun euDotClassGroupsWithDotCommaDecimalSymbolBefore() {
        // de/da/es/pt/tr/vi — e.g. de "€1.000.000,00"
        val p = NumberFormatProfile.EU_DOT
        assertEquals("€1.234.567,89", Money(123_456_789, 2, "EUR").formatted(p))
        assertEquals("€0,05", Money(5, 2, "EUR").formatted(p))
        assertEquals("-€1.234,56", Money(-123_456, 2, "EUR").formatted(p))
        assertEquals("+€123,45", Money(12_345, 2, "EUR").formattedSigned(p))
        assertEquals("25,00%", formatBps(2_500, p))
        assertEquals("-0,05%", formatBps(-5, p))
    }

    @Test
    fun euSpaceClassGroupsWithSpaceCommaDecimalSymbolAfter() {
        // fr/no/pl/ru/sv — e.g. fr "1 000 000,00 €"
        val p = NumberFormatProfile.EU_SPACE
        assertEquals("1${nbsp}234${nbsp}567,89${nbsp}€", Money(123_456_789, 2, "EUR").formatted(p))
        assertEquals("-1${nbsp}234,56${nbsp}€", Money(-123_456, 2, "EUR").formatted(p))
        assertEquals("+123,45${nbsp}€", Money(12_345, 2, "EUR").formattedSigned(p))
        assertEquals("12,34%", formatBps(1_234, p))
    }

    @Test
    fun usClassIsTheDefault() {
        // en/ar/zh — group ",", decimal "."
        assertEquals("$1,234.56", Money(123_456, 2, "USD").formatted(NumberFormatProfile.US))
        assertEquals(Money(123_456, 2, "USD").formatted(), Money(123_456, 2, "USD").formatted(NumberFormatProfile.US))
    }

    @Test
    fun languageTagResolvesToFormatClass() {
        assertEquals(NumberFormatProfile.EU_DOT, NumberFormatProfile.forLanguageTag("de"))
        assertEquals(NumberFormatProfile.EU_DOT, NumberFormatProfile.forLanguageTag("pt-BR"))
        assertEquals(NumberFormatProfile.EU_DOT, NumberFormatProfile.forLanguageTag("vi"))
        assertEquals(NumberFormatProfile.EU_SPACE, NumberFormatProfile.forLanguageTag("fr-FR"))
        assertEquals(NumberFormatProfile.EU_SPACE, NumberFormatProfile.forLanguageTag("ru"))
        assertEquals(NumberFormatProfile.US, NumberFormatProfile.forLanguageTag("en-US"))
        assertEquals(NumberFormatProfile.US, NumberFormatProfile.forLanguageTag("zh-Hans-CN"))
        assertEquals(NumberFormatProfile.US, NumberFormatProfile.forLanguageTag("ar"))
        assertEquals(NumberFormatProfile.US, NumberFormatProfile.forLanguageTag("xx")) // unknown → US
    }
}
