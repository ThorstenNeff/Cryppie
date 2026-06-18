package com.tneff.cyppie.portfolio

import com.tneff.cyppie.rpc.AlchemyPriceClient

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
}
