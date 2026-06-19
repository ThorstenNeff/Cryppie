package com.tneff.cyppie.market

/**
 * Price-decimal fixed-point helpers (ADR-0025 Phase 2) — the canonical home of the FR-6 string→fixed-point
 * conversion, shared by the price adapters (`AlchemyPriceSource` here, the upcoming `BridgeMarketDataApi`) and
 * `:portfolio`'s `Valuation` (which delegates here). No floating point — deterministic, test-checkable.
 */

/** A price string is parsed to `price × 10^8` (sub-cent precision). */
const val PRICE_SCALE: Int = 8

/**
 * Parses a non-negative decimal price string ("2543.12", "0.9998") into a fixed-point [Long] at [scale]
 * (× 10^scale, extra fraction digits truncated). Returns `null` if not a non-negative number.
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
