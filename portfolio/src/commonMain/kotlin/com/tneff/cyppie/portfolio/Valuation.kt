package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.parseDecimalToScaled as marketParseDecimalToScaled

import com.tneff.cyppie.evm.Quantity

/**
 * Big-int-safe valuation math (PO note b): `rawBalance(wei) × price` is a >64-bit intermediate, the
 * fiat result fits Long. No floating point → deterministic / test-checkable (FR-6).
 *
 * ADR-0025 Phase 2: the price-decimal scale + string→fixed-point parse now live canonically in `:market`
 * ([com.tneff.cyppie.market.PRICE_SCALE] / [com.tneff.cyppie.market.parseDecimalToScaled]); this object
 * delegates so `:portfolio`'s valuation API is unchanged but there's a single source of truth.
 */
internal object Valuation {

    val PRICE_SCALE: Int = com.tneff.cyppie.market.PRICE_SCALE // single source: :market
    const val MONEY_SCALE = 2 // fiat money is held in cents

    /** Delegates to [com.tneff.cyppie.market.parseDecimalToScaled] (single source). */
    fun parseDecimalToScaled(s: String, scale: Int): Long? = marketParseDecimalToScaled(s, scale)

    /**
     * Fiat value (in [MONEY_SCALE] minor units = cents) of [rawBalance] base units of a token with
     * [decimals], at [price] per whole token:
     * `value_cents = rawBalance × price.minorUnits / 10^(decimals + price.scale − 2)`.
     * The product is 256-bit; the cents result fits Long (capped on absurd inputs — spam tokens).
     */
    fun valueCents(rawBalance: Quantity, decimals: Int, price: Money): Long {
        if (price.minorUnits <= 0 || rawBalance.isZero) return 0
        val product = rawBalance * Quantity.of(price.minorUnits)
        val exp = decimals + price.scale - MONEY_SCALE
        val scaled = if (exp >= 0) product.divPow10(exp) else product
        return runCatching { scaled.toLong() }.getOrDefault(Long.MAX_VALUE)
    }
}
