package com.tneff.cyppie.market

import com.tneff.cyppie.rpc.AlchemyPriceClient
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * On-device [PriceSource] over the Alchemy Prices API (ADR-0017 MVP) — relocated to `:market` and re-typed
 * over [MarketAsset] (ADR-0025 Phase 2). Prices by contract address per network; the decimal string is
 * parsed to a fixed-point [Money] at [PRICE_SCALE] **once here** (no float, FR-6). [clockEpochSeconds] stamps
 * the fetch so callers can check [TokenPrice.isStale].
 *
 * Only ERC-20 ([MarketAsset.Erc20]) assets are priced upstream; native coins are mapped to their chain's WETH
 * by the caller (`PortfolioService.priceTokenFor`) before pricing, so a [MarketAsset.Native] yields no price.
 * The PRD-04 `BridgeMarketDataApi` will supersede this behind the same [PriceSource] interface (DI swap).
 */
class AlchemyPriceSource(
    private val client: AlchemyPriceClient,
    private val clockEpochSeconds: () -> Long,
) : PriceSource {

    override suspend fun currentPrices(assets: List<MarketAsset>, vs: String): Map<MarketAsset, TokenPrice> {
        val byKey = assets.mapNotNull { a -> (a as? MarketAsset.Erc20)?.let { (it.chainId to it.contract) to a } }.toMap()
        if (byKey.isEmpty()) return emptyMap()
        val raw = client.pricesByAddress(byKey.keys.toList(), vs)
        val now = clockEpochSeconds()
        return raw.mapNotNull { r ->
            val asset = byKey[r.chainId to r.contract] ?: return@mapNotNull null
            val scaled = parseDecimalToScaled(r.priceDecimal, PRICE_SCALE) ?: return@mapNotNull null
            asset to TokenPrice(Money(scaled, PRICE_SCALE, r.currency), now)
        }.toMap()
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun priceHistory(
        asset: MarketAsset,
        vs: String,
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
        intervalSeconds: Long,
    ): List<FiatPricePoint> {
        val erc20 = asset as? MarketAsset.Erc20 ?: return emptyList()
        val raw = client.historicalByAddress(
            chainId = erc20.chainId,
            contract = erc20.contract,
            startTimeIso = Instant.fromEpochSeconds(fromEpochSeconds).toString(),
            endTimeIso = Instant.fromEpochSeconds(toEpochSeconds).toString(),
            interval = alchemyInterval(intervalSeconds),
        )
        return raw.mapNotNull { p ->
            val epoch = p.timestampIso?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() } ?: return@mapNotNull null
            val scaled = parseDecimalToScaled(p.priceDecimal, PRICE_SCALE) ?: return@mapNotNull null
            FiatPricePoint(epoch, Money(scaled, PRICE_SCALE, vs))
        }.sortedBy { it.epochSeconds }
    }

    /** Maps a sampling interval (seconds) to Alchemy's supported granularity. */
    private fun alchemyInterval(seconds: Long): String = when {
        seconds >= 86_400 -> "1d"
        seconds >= 3_600 -> "1h"
        seconds >= 300 -> "5m"
        else -> "1m"
    }
}
