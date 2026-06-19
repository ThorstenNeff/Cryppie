package com.tneff.cyppie.market

/**
 * The app-facing market-data contract (PRD-04 §5) — a **superset** of the (held) `PriceSource` (it adds
 * OHLC candles for charts). The MVP binding composes the [CoinGeckoMarketClient] (per-contract spot/history,
 * via the key-proxy) and [BinanceMarketClient] (keyless candles for major pairs); the full PRD-08
 * Market-Data-Service (Redis cache, provider failover, server-side indicators) supersedes it behind this
 * same interface, so swapping the binding is a DI change with no caller impact.
 */
interface MarketDataApi {

    /** OHLC candles for [asset] at [interval] over [range]. */
    suspend fun candles(asset: MarketAsset, interval: CandleInterval, range: TimeRange): List<Candle>

    /** Current spot prices for [assets] in [vs] fiat (assets with no price are omitted from the map). */
    suspend fun spotPrices(assets: List<MarketAsset>, vs: String): Map<MarketAsset, SpotPrice>

    /** Historical price samples for [asset] in [vs] fiat over [range] at [interval]. */
    suspend fun priceHistory(asset: MarketAsset, vs: String, interval: CandleInterval, range: TimeRange): List<PricePoint>
}

/** Market-data failure (upstream error, unmappable asset, malformed payload). Fail-closed at the edges. */
sealed class MarketException(message: String) : Exception(message) {
    /** The asset can't be mapped to the chosen upstream (e.g. no Binance symbol / CoinGecko id). */
    class Unsupported(message: String) : MarketException(message)
    /** The upstream returned a non-success status or an unparseable body. */
    class Upstream(message: String) : MarketException(message)
}
