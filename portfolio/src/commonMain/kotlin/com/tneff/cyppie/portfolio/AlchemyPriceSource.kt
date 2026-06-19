package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.FiatPricePoint
import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.TokenPrice
import com.tneff.cyppie.rpc.AlchemyPriceClient
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [PriceSource] over the Alchemy Prices client (KAN-98 Stage A). Parses each price string into a
 * fixed-point [Money] at [Valuation.PRICE_SCALE] (no float). [clockEpochSeconds] stamps the fetch
 * time so the valuator can flag staleness (Q8). 24h change isn't in the basic by-address response →
 * null (Slice P4). Native ETH is priced by mapping it to its WETH token upstream (caller's job).
 */
class AlchemyPriceSource(
    private val client: AlchemyPriceClient,
    private val clockEpochSeconds: () -> Long,
) : PriceSource {

    override suspend fun currentPrices(tokens: List<PortfolioToken>, vs: String): Map<PortfolioToken, TokenPrice> {
        val byKey = tokens.mapNotNull { t -> t.contract?.let { (t.chainId to it) to t } }.toMap()
        if (byKey.isEmpty()) return emptyMap()
        val raw = client.pricesByAddress(byKey.keys.toList(), vs)
        val now = clockEpochSeconds()
        return raw.mapNotNull { r ->
            val token = byKey[r.chainId to r.contract] ?: return@mapNotNull null
            val scaled = Valuation.parseDecimalToScaled(r.priceDecimal, Valuation.PRICE_SCALE) ?: return@mapNotNull null
            token to TokenPrice(Money(scaled, Valuation.PRICE_SCALE, r.currency), now)
        }.toMap()
    }

    /**
     * Historical prices via Alchemy Historical Prices. ISO↔epoch uses the **stdlib** `kotlin.time.Instant`
     * (Kotlin 2.4) — no extra dependency, no dependency-verification entry needed. Native tokens (no
     * contract) yield nothing here; price native ETH via its WETH token upstream.
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun priceHistory(
        token: PortfolioToken,
        vs: String,
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
        intervalSeconds: Long,
    ): List<FiatPricePoint> {
        val contract = token.contract ?: return emptyList()
        val raw = client.historicalByAddress(
            chainId = token.chainId,
            contract = contract,
            startTimeIso = Instant.fromEpochSeconds(fromEpochSeconds).toString(),
            endTimeIso = Instant.fromEpochSeconds(toEpochSeconds).toString(),
            interval = alchemyInterval(intervalSeconds),
        )
        return raw.mapNotNull { p ->
            val epoch = p.timestampIso?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() } ?: return@mapNotNull null
            val scaled = Valuation.parseDecimalToScaled(p.priceDecimal, Valuation.PRICE_SCALE) ?: return@mapNotNull null
            FiatPricePoint(epoch, Money(scaled, Valuation.PRICE_SCALE, vs))
        }.sortedBy { it.epochSeconds }
    }

    private fun alchemyInterval(seconds: Long): String = when {
        seconds >= 86_400 -> "1d"
        seconds >= 3_600 -> "1h"
        seconds >= 300 -> "5m"
        else -> "1m"
    }
}
