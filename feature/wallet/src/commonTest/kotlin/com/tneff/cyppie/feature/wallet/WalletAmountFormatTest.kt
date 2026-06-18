package com.tneff.cyppie.feature.wallet

import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletAmountFormatTest {

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
