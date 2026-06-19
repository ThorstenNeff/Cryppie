package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money

import com.tneff.cyppie.evm.Quantity

/**
 * Big-int-safe valuation math (PO note b): `rawBalance(wei) × price` is a >64-bit intermediate, the
 * fiat result fits Long. No floating point → deterministic / test-checkable (FR-6).
 */
internal object Valuation {

    const val PRICE_SCALE = 8 // a price string is parsed to price × 10^8 (sub-cent precision)
    const val MONEY_SCALE = 2 // fiat money is held in cents

    /**
     * Parses a non-negative decimal price string ("2543.12", "0.9998") into a fixed-point Long at
     * [scale] (× 10^scale, extra fraction digits truncated). Returns null if not a non-negative number.
     */
    fun parseDecimalToScaled(s: String, scale: Int): Long? {
        val t = s.trim()
        if (t.isEmpty() || t.startsWith("-")) return null
        val dot = t.indexOf('.')
        val intPart = if (dot < 0) t else t.substring(0, dot)
        val fracRaw = if (dot < 0) "" else t.substring(dot + 1)
        if (intPart.any { it !in '0'..'9' } || fracRaw.any { it !in '0'..'9' }) return null
        val frac = if (fracRaw.length >= scale) fracRaw.substring(0, scale) else fracRaw.padEnd(scale, '0')
        return (intPart + frac).ifEmpty { "0" }.toLongOrNull()
    }

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
