package com.tneff.cyppie.market

/**
 * The portfolio-valuation pricing contract (ADR-0017) — now in `:market`, **typed over [MarketAsset]**
 * (ADR-0025 Phase 2 reconcile). The MVP impl is [AlchemyPriceSource] (on-device, Alchemy via the proxy);
 * PRD-04's `BridgeMarketDataApi` will also implement this (it's the spot+history subset of `MarketDataApi`),
 * so the source swaps by DI with no caller change.
 *
 * Values are domain-level [Money]/[FiatPricePoint] (fixed-point, FR-6) — the string→Money conversion happens
 * **once at the adapter edge** (the impl), via [parseDecimalToScaled]; consumers never see floats or strings.
 */
interface PriceSource {

    /** Current prices for [assets] in [vs] fiat, keyed by asset. Assets with no price are omitted. */
    suspend fun currentPrices(assets: List<MarketAsset>, vs: String): Map<MarketAsset, TokenPrice>

    /**
     * Historical fiat price series for [asset] in [vs] over [[fromEpochSeconds], [toEpochSeconds]] sampled at
     * [intervalSeconds]. Default empty so current-only sources need not implement it.
     */
    suspend fun priceHistory(
        asset: MarketAsset,
        vs: String,
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
        intervalSeconds: Long,
    ): List<FiatPricePoint> = emptyList()
}
