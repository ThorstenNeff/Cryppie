package com.tneff.cyppie.market

/**
 * A fiat amount as a fixed-point decimal: value = [minorUnits] / 10^[scale] (e.g. scale=2 → cents).
 * Integer-based to keep valuation deterministic + test-checkable (FR-6) — no floating point.
 *
 * The single-source money type for the app (ADR-0025): relocated from `:portfolio` to the foundational
 * `:market` so pricing/portfolio/wallet share one `Money` cycle-free. (`:market`'s own market-data
 * model keeps decimal Strings for raw OHLC/spot precision; `Money` is the domain/valuation boundary.)
 */
data class Money(val minorUnits: Long, val scale: Int, val currency: String) {

    /**
     * Sum of two amounts. Guards currency + scale equality (PO note: two `Money` with the same value
     * but a different [scale] are unequal under the data-class `equals`, so summing must not silently
     * mix scales). Normalize first with [atScale] if scales differ.
     */
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "currency mismatch: $currency vs ${other.currency}" }
        require(scale == other.scale) { "scale mismatch: $scale vs ${other.scale} (normalize with atScale first)" }
        // Saturating add (KAN-100 L1, defensive): real values are bounded (spam/dust filtered before
        // valuation), but capped-absurd inputs must never silently wrap a portfolio total.
        val sum = minorUnits + other.minorUnits
        val saturated = if ((minorUnits xor sum) and (other.minorUnits xor sum) < 0L) {
            if (minorUnits > 0L) Long.MAX_VALUE else Long.MIN_VALUE
        } else {
            sum
        }
        return Money(saturated, scale, currency)
    }

    /** Re-scales to [newScale] (only upscaling is exact; downscaling truncates). */
    fun atScale(newScale: Int): Money = when {
        newScale == scale -> this
        newScale > scale -> Money(minorUnits * pow10(newScale - scale), newScale, currency)
        else -> Money(minorUnits / pow10(scale - newScale), newScale, currency)
    }

    companion object {
        fun zero(currency: String, scale: Int = 2): Money = Money(0, scale, currency)

        private fun pow10(n: Int): Long {
            var r = 1L
            repeat(n) { r *= 10 }
            return r
        }
    }
}
