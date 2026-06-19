package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.FiatPricePoint
import com.tneff.cyppie.market.TokenPrice

/**
 * Pricing abstraction (PRD-03 Q4): the MVP impl is Alchemy Prices (Stage 2); migratable to the PRD-04
 * market-data service without touching callers. Read-only. [vs] is the display fiat (default =
 * device-locale currency, fallback USD; FR-2). Tokens with no available price are absent from the map.
 *
 * ADR-0025 Phase 1: [TokenPrice]/[FiatPricePoint]/`Money` now live in `:market` (single-source). The
 * interface stays here for now (typed over [PortfolioToken]); Phase 2 (reconcile with Dev-2) re-types
 * it over `:market`'s `MarketAsset` and relocates `PriceSource`/`AlchemyPriceSource` into `:market`.
 */
interface PriceSource {
    suspend fun currentPrices(tokens: List<PortfolioToken>, vs: String): Map<PortfolioToken, TokenPrice>

    /**
     * Historical fiat prices for [token] in [vs] across [fromEpochSeconds]..[toEpochSeconds] at roughly
     * [intervalSeconds] spacing — feeds the value-over-time series + 24h change (KAN-102 P4). Default =
     * empty so current-price-only callers/fakes need not implement it.
     */
    suspend fun priceHistory(
        token: PortfolioToken,
        vs: String,
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
        intervalSeconds: Long,
    ): List<FiatPricePoint> = emptyList()
}
