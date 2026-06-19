package com.tneff.cyppie.market

/**
 * A historical fiat price sample: [price] as of [epochSeconds]. Money-valued (the domain/valuation
 * representation, ADR-0025) — relocated from `:portfolio` (was `PricePoint`) and **renamed** to avoid
 * a name clash with `:market`'s raw decimal-String `PricePoint`. The two are unified in the Phase-2
 * reconcile (PriceSource re-typed over MarketAsset; one coherent foundation).
 */
data class FiatPricePoint(val epochSeconds: Long, val price: Money)

/**
 * A fiat price for one token at [asOfEpochSeconds], so a caller can flag staleness (FR-2 / Q8 TTL 60s).
 * [change24hBps] is the 24h change in basis points (null if unknown). Relocated from `:portfolio`
 * (ADR-0025).
 */
data class TokenPrice(
    val price: Money,
    val asOfEpochSeconds: Long,
    val change24hBps: Int? = null,
) {
    /** Stale if older than [maxAgeSeconds] (price cache TTL = 60s, Q8) relative to [nowEpochSeconds]. */
    fun isStale(nowEpochSeconds: Long, maxAgeSeconds: Long = PRICE_TTL_SECONDS): Boolean =
        nowEpochSeconds - asOfEpochSeconds > maxAgeSeconds

    companion object {
        const val PRICE_TTL_SECONDS: Long = 60 // Q8
        const val BALANCE_TTL_SECONDS: Long = 30 // Q8
    }
}
