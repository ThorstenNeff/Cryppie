package com.tneff.cyppie.market

/**
 * The MVP **market-data bridge** (PRD-04 / KAN-132): one class composing the two app-side adapters
 * ([CoinGeckoMarketClient] + [BinanceMarketClient]) behind **both** the app-facing [MarketDataApi] **and**
 * the valuation [PriceSource]. Spot + history are the shared subset, so this *is* the
 * `MarketDataPriceSource` that PRD-03 migrates to (ADR-0017): DI binds `PriceSource` → this, no caller change.
 *
 * **Routing is per (asset, data-type)** — the two sources have different strengths (see the design doc):
 * - **spot**: CoinGecko (ERC-20 by-contract batched per platform, native coins by coin-id), any fiat;
 *   Binance last-close fallback only for unresolved USD majors.
 * - **candles**: Binance klines for major pairs (granular, keyless) → else CoinGecko `/ohlc` by coin-id →
 *   else [MarketException.Unsupported] (caller renders the [priceHistory] line series instead).
 * - **history**: CoinGecko `market_chart/range` by contract (any fiat) → else Binance kline closes (USD only).
 * - **stats**: CoinGecko `/coins/markets` by coin-id, one batched call.
 *
 * **Failover is app-side MVP only**: primary → fallback (iff it can serve this asset+fiat) → **degrade**.
 * There is **no app-side cache** (PRD-08 owns Redis/stale-serving), so "degrade" = an **empty result**
 * (the VM renders its empty/error state) — never served-stale. The full PRD-08 service supersedes this
 * behind the same interfaces. [clockEpochSeconds] stamps spot prices that lack an upstream timestamp.
 *
 * Non-USD fiat ⇒ CoinGecko only (Binance is effectively USD via USDT pairs); the Binance fallbacks are
 * gated on [isUsdLike].
 */
class BridgeMarketDataApi(
    private val coinGecko: CoinGeckoMarketClient,
    private val binance: BinanceMarketClient,
    private val catalog: MarketAssetCatalog = DefaultMarketAssetCatalog,
    private val clockEpochSeconds: () -> Long,
) : MarketDataApi, PriceSource {

    // ---- MarketDataApi -------------------------------------------------------------------------

    override suspend fun candles(asset: MarketAsset, interval: CandleInterval, range: TimeRange): List<Candle> {
        val symbol = catalog.binanceSymbol(asset)
        val coinId = catalog.coinGeckoId(asset)
        if (symbol == null && coinId == null) {
            throw MarketException.Unsupported("no candle source for $asset")
        }
        // Binance first (real OHLCV, granular), then CoinGecko /ohlc by id; first non-empty wins, else degrade.
        val sources = buildList<suspend () -> List<Candle>> {
            symbol?.let { add { binance.klines(it, interval, range.fromEpochSeconds, range.toEpochSeconds) } }
            coinId?.let { add { coinGecko.coinOhlc(it, CHART_VS, coinGeckoDays(range)) } }
        }
        return firstNonEmpty(sources)
    }

    override suspend fun spotPrices(assets: List<MarketAsset>, vs: String): Map<MarketAsset, SpotPrice> {
        val out = mutableMapOf<MarketAsset, SpotPrice>()

        // ERC-20 by contract, one batched CoinGecko call per platform (efficient).
        assets.filterIsInstance<MarketAsset.Erc20>()
            .groupBy { catalog.coinGeckoPlatform(it.chainId) }
            .forEach { (platform, group) ->
                if (platform == null) return@forEach
                val byContract = group.associateBy { it.contract.hexLower() }
                val res = runCatching { coinGecko.tokenSpotPrices(platform, byContract.keys.toList(), vs) }
                    .getOrElse { emptyMap() }
                res.forEach { (contract, sp) -> byContract[contract]?.let { out[it] = sp } }
            }

        // Native coins by coin-id (no contract) — one batched /simple/price call.
        val nativeIds = assets.filterIsInstance<MarketAsset.Native>()
            .mapNotNull { n -> catalog.coinGeckoId(n)?.let { it to n } }
        if (nativeIds.isNotEmpty()) {
            val res = runCatching { coinGecko.coinSpotPrices(nativeIds.map { it.first }.distinct(), vs) }
                .getOrElse { emptyMap() }
            nativeIds.forEach { (id, n) -> res[id]?.let { out[n] = it } }
        }

        // Fallback: unresolved USD majors → Binance last-kline close.
        if (isUsdLike(vs)) {
            for (asset in assets) {
                if (out.containsKey(asset)) continue
                val symbol = catalog.binanceSymbol(asset) ?: continue
                val close = runCatching { binance.klines(symbol, CandleInterval.M1, limit = 1) }
                    .getOrNull()?.lastOrNull()?.close ?: continue
                out[asset] = SpotPrice(priceDecimal = close, vs = vs)
            }
        }
        return out
    }

    override suspend fun priceHistory(
        asset: MarketAsset,
        vs: String,
        interval: CandleInterval,
        range: TimeRange,
    ): List<PricePoint> {
        // Primary: CoinGecko by contract (any fiat).
        val erc20 = asset as? MarketAsset.Erc20
        val platform = erc20?.let { catalog.coinGeckoPlatform(it.chainId) }
        if (erc20 != null && platform != null) {
            val r = runCatching {
                coinGecko.contractMarketChartRange(
                    platform, erc20.contract.hexLower(), vs, range.fromEpochSeconds, range.toEpochSeconds,
                )
            }.getOrNull()
            if (!r.isNullOrEmpty()) return r
        }
        // Fallback: Binance kline closes (USD only).
        if (isUsdLike(vs)) {
            val symbol = catalog.binanceSymbol(asset)
            if (symbol != null) {
                val candles = runCatching {
                    binance.klines(symbol, interval, range.fromEpochSeconds, range.toEpochSeconds)
                }.getOrNull()
                if (!candles.isNullOrEmpty()) return candles.map { PricePoint(it.openEpochSeconds, it.close) }
            }
        }
        return emptyList() // degrade
    }

    override suspend fun marketStats(assets: List<MarketAsset>, vs: String): Map<MarketAsset, MarketStats> {
        val idByAsset = assets.mapNotNull { a -> catalog.coinGeckoId(a)?.let { a to it } }
        if (idByAsset.isEmpty()) return emptyMap()
        val res = runCatching { coinGecko.coinsMarkets(idByAsset.map { it.second }.distinct(), vs) }
            .getOrElse { return emptyMap() }
        return idByAsset.mapNotNull { (asset, id) -> res[id]?.let { asset to it } }.toMap()
    }

    // ---- PriceSource (spot + history subset, Money-valued at this boundary) ---------------------

    override suspend fun currentPrices(assets: List<MarketAsset>, vs: String): Map<MarketAsset, TokenPrice> {
        val now = clockEpochSeconds()
        // Convert the raw decimal string → fixed-point Money exactly **once**, here at the PriceSource edge.
        return spotPrices(assets, vs).mapNotNull { (asset, sp) ->
            val scaled = parseDecimalToScaled(sp.priceDecimal, PRICE_SCALE) ?: return@mapNotNull null
            asset to TokenPrice(
                price = Money(scaled, PRICE_SCALE, sp.vs),
                asOfEpochSeconds = sp.lastUpdatedEpochSeconds ?: now,
                change24hBps = pctToBps(sp.change24hPct),
            )
        }.toMap()
    }

    override suspend fun priceHistory(
        asset: MarketAsset,
        vs: String,
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
        intervalSeconds: Long,
    ): List<FiatPricePoint> {
        val points = priceHistory(asset, vs, candleIntervalFor(intervalSeconds), TimeRange(fromEpochSeconds, toEpochSeconds))
        return points.mapNotNull { p ->
            val scaled = parseDecimalToScaled(p.priceDecimal, PRICE_SCALE) ?: return@mapNotNull null
            FiatPricePoint(p.epochSeconds, Money(scaled, PRICE_SCALE, vs))
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Returns the first source that yields a non-empty list (ignoring upstream errors); else empty (degrade). */
    private suspend fun firstNonEmpty(sources: List<suspend () -> List<Candle>>): List<Candle> {
        for (source in sources) {
            val r = runCatching { source() }.getOrNull()
            if (!r.isNullOrEmpty()) return r
        }
        return emptyList()
    }

    private companion object {
        const val CHART_VS = "usd" // candles are USD-centric (CoinGecko /ohlc + Binance USDT pairs)

        /** Binance pairs are USDT (≈USD); any other fiat must go through CoinGecko. */
        fun isUsdLike(vs: String): Boolean = vs.equals("usd", ignoreCase = true) || vs.equals("usdt", ignoreCase = true)

        /** 24h change percent string ("-1.25") → basis points (-125). Sign-aware; null-safe. */
        fun pctToBps(pct: String?): Int? {
            val t = pct?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val negative = t.startsWith("-")
            val magnitude = parseDecimalToScaled(t.removePrefix("-").removePrefix("+"), 2) ?: return null
            val capped = magnitude.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return if (negative) -capped else capped
        }

        /** Maps a [TimeRange] span to a CoinGecko `/ohlc` `days` bucket. */
        fun coinGeckoDays(range: TimeRange): String {
            val spanSeconds = (range.toEpochSeconds - range.fromEpochSeconds).coerceAtLeast(1)
            val spanDays = (spanSeconds + 86_399) / 86_400
            return when {
                spanDays <= 1 -> "1"
                spanDays <= 7 -> "7"
                spanDays <= 14 -> "14"
                spanDays <= 30 -> "30"
                spanDays <= 90 -> "90"
                spanDays <= 180 -> "180"
                spanDays <= 365 -> "365"
                else -> "max"
            }
        }

        /** Maps a sampling interval (seconds) to the nearest [CandleInterval] bar. */
        fun candleIntervalFor(seconds: Long): CandleInterval = when {
            seconds >= 86_400 -> CandleInterval.D1
            seconds >= 14_400 -> CandleInterval.H4
            seconds >= 3_600 -> CandleInterval.H1
            seconds >= 900 -> CandleInterval.M15
            seconds >= 300 -> CandleInterval.M5
            else -> CandleInterval.M1
        }
    }
}
