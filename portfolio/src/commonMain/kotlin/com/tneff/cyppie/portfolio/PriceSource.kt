package com.tneff.cyppie.portfolio

/**
 * Pricing abstraction (PRD-03 Q4): the MVP impl is Alchemy Prices (Stage 2); migratable to the PRD-04
 * market-data service without touching callers. Read-only. [vs] is the display fiat (default =
 * device-locale currency, fallback USD; FR-2). Tokens with no available price are absent from the map.
 */
interface PriceSource {
    suspend fun currentPrices(tokens: List<PortfolioToken>, vs: String): Map<PortfolioToken, TokenPrice>

    /**
     * Historical fiat prices for [token] in [vs] across [fromEpochSeconds]..[toEpochSeconds] at roughly
     * [intervalSeconds] spacing — feeds the value-over-time series + 24h change (KAN-102 P4). The
     * PRD-04 market-data service implements the **same** interface (Q4 drop-in). Default = empty so
     * current-price-only callers/fakes need not implement it.
     */
    suspend fun priceHistory(
        token: PortfolioToken,
        vs: String,
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
        intervalSeconds: Long,
    ): List<PricePoint> = emptyList()
}

/** A historical price sample: [price] in fiat as of [epochSeconds]. */
data class PricePoint(val epochSeconds: Long, val price: Money)

/**
 * A fiat price for one token at [asOfEpochSeconds], so a caller can flag staleness (FR-2 / Q8 TTL 60s).
 * [change24hBps] is the 24h change in basis points (null if unknown).
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
