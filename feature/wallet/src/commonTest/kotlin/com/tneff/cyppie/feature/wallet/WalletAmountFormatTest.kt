package com.tneff.cyppie.feature.wallet

import com.tneff.cyppie.designsystem.NumberFormatProfile
import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletAmountFormatTest {

    private fun amount(decimal: String, decimals: Int) = parseTokenAmount(decimal, decimals)!!

    // ---- KAN-120: locale-aware display formatting (grouping + decimal per NumberFormatProfile) ----

    @Test
    fun localeGroupingAndDecimalSeparators() {
        val a = amount("1234567.89", 18) // big value (> Long), must stay big-integer-safe
        assertEquals("1,234,567.89", formatTokenAmount(a, 18, NumberFormatProfile.US))     // en/ar/zh
        assertEquals("1.234.567,89", formatTokenAmount(a, 18, NumberFormatProfile.EU_DOT)) // de/da/es/pt/vi
        assertEquals("1 234 567,89", formatTokenAmount(a, 18, NumberFormatProfile.EU_SPACE)) // NBSP grouping // fr/no/pl/ru/sv (NBSP group)
    }

    @Test
    fun plainFormStaysParseableForMax() {
        // The no-profile form keeps "." + no grouping so the Send Max → input field round-trips.
        val a = amount("1234567.5", 18)
        assertEquals("1234567.5", formatTokenAmount(a, 18))
        assertEquals(a, parseTokenAmount(formatTokenAmount(a, 18), 18))
    }

    @Test
    fun wholeNumberHasNoDecimalSeparatorUnderProfile() {
        val a = amount("1000", 18)
        assertEquals("1,000", formatTokenAmount(a, 18, NumberFormatProfile.US))
        assertEquals("1.000", formatTokenAmount(a, 18, NumberFormatProfile.EU_DOT))
    }

    @Test
    fun erc20SixDecimalsUnderProfile() {
        val a = amount("2500.5", 6)
        assertEquals("2,500.5", formatTokenAmount(a, 6, NumberFormatProfile.US))
        assertEquals("2.500,5", formatTokenAmount(a, 6, NumberFormatProfile.EU_DOT))
    }

    @Test
    fun profileResolvesPerLocale() {
        assertEquals(NumberFormatProfile.EU_DOT, NumberFormatProfile.forLanguageTag("de"))
        assertEquals(NumberFormatProfile.EU_SPACE, NumberFormatProfile.forLanguageTag("fr-FR"))
        assertEquals(NumberFormatProfile.US, NumberFormatProfile.forLanguageTag("en"))
        assertEquals(NumberFormatProfile.US, NumberFormatProfile.forLanguageTag("ar"))
        assertNotNull(NumberFormatProfile.forLanguageTag("xx")) // unknown → US fallback
    }

    @Test
    fun zero_is_zero() {
        assertEquals("0", formatTokenAmount(Quantity.of(0), 18))
    }

    @Test
    fun whole_native_amount() {
        assertEquals("1", formatTokenAmount(Quantity.of(1_000_000_000_000_000_000L), 18))
    }

    @Test
    fun fractional_native_trims_trailing_zeros() {
        assertEquals("1.5", formatTokenAmount(Quantity.of(1_500_000_000_000_000_000L), 18))
    }

    @Test
    fun erc20_six_decimals() {
        // 1.5 USDC (6 decimals)
        assertEquals("1.5", formatTokenAmount(Quantity.of(1_500_000L), 6))
    }

    @Test
    fun amount_beyond_long_range_uses_bigint_path() {
        // 100 ETH = 1e20 wei > Long.MAX — must not overflow.
        assertEquals("100", formatTokenAmount(Quantity.ofHex("0x56bc75e2d63100000"), 18))
    }

    @Test
    fun fraction_is_truncated_to_max_digits_not_rounded() {
        // 1.2345678 ETH, cap 6 → truncated "1.234567" (never overstate)
        assertEquals("1.234567", formatTokenAmount(Quantity.of(1_234_567_800_000_000_000L), 18, maxFractionDigits = 6))
    }

    @Test
    fun sub_display_dust_shows_zero() {
        // 1 wei with 6 fraction digits → below display precision → "0"
        assertEquals("0", formatTokenAmount(Quantity.of(1L), 18))
    }

    @Test
    fun zero_decimals_returns_integer() {
        assertEquals("42", formatTokenAmount(Quantity.of(42L), 0))
    }
}
