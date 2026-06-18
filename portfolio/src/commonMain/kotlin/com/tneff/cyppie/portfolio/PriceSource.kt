package com.tneff.cyppie.portfolio

/**
 * Pricing abstraction (PRD-03 Q4): the MVP impl is Alchemy Prices (Stage 2); migratable to the PRD-04
 * market-data service without touching callers. Read-only. [vs] is the display fiat (default =
 * device-locale currency, fallback USD; FR-2). Tokens with no available price are absent from the map.
 */
interface PriceSource {
    suspend fun currentPrices(tokens: List<PortfolioToken>, vs: String): Map<PortfolioToken, TokenPrice>
}

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
