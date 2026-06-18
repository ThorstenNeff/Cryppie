package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.EvmAddress

/** Identity for the known-good allowlist (chainId + contract; native = null contract). */
data class TokenKey(val chainId: Long, val contract: EvmAddress?)

/**
 * Spam/curation config (Q5): a token shows if it is **catalog-known-good** OR its fiat value ≥
 * [dustThresholdCents] — not a strict allowlist (real holdings outside the catalog still show). The
 * caller passes the known-good set (from `TokenCatalog`, which lives in non-web `:walletcore`) and
 * should pre-filter Alchemy spam-flagged tokens. [currency] is the display fiat.
 */
data class PortfolioConfig(
    val knownGood: Set<TokenKey>,
    val currency: String,
    val dustThresholdCents: Long = 1,
)

/**
 * Values priced holdings into a [Portfolio] with the **robust** P2 headline metrics: total value +
 * allocation (sums to exactly 10_000 bps). Any stale price (Q8) marks the total as
 * [Metric.approximate] with [ApproxReason.STALE_PRICES]. Dust/spam is filtered per [PortfolioConfig].
 * 24h change + the rich/time-series metrics are Slice P4 (separate story).
 */
object PortfolioValuator {

    fun value(
        holdings: List<Holding>,
        prices: Map<PortfolioToken, TokenPrice>,
        config: PortfolioConfig,
        nowEpochSeconds: Long,
    ): Portfolio {
        var anyStale = false
        val valued = holdings.map { h ->
            val price = prices[h.token]
            val money = if (price != null) {
                if (price.isStale(nowEpochSeconds)) anyStale = true
                Money(
                    Valuation.valueCents(h.rawBalance, h.token.decimals, price.price),
                    Valuation.MONEY_SCALE,
                    config.currency,
                )
            } else {
                null
            }
            h.copy(value = money)
        }

        val kept = valued.filter { h ->
            TokenKey(h.token.chainId, h.token.contract) in config.knownGood ||
                (h.value?.minorUnits ?: 0L) >= config.dustThresholdCents
        }

        val priced = kept.filter { it.value != null }
        val total = priced.fold(Money.zero(config.currency)) { acc, h -> acc + h.value!! }
        val totalMetric = Metric(
            total,
            if (anyStale) MetricConfidence.Approximate(listOf(ApproxReason.STALE_PRICES)) else MetricConfidence.Robust,
        )
        return Portfolio(
            holdings = kept,
            totalValue = totalMetric,
            change24h = null, // Slice P4
            allocation = allocationOf(priced, total),
        )
    }

    /** Basis-point slices; the largest absorbs the rounding remainder so the allocation sums to 10_000. */
    private fun allocationOf(priced: List<Holding>, total: Money): List<AllocationSlice> {
        if (total.minorUnits <= 0L) return emptyList()
        val slices = priced
            .mapNotNull { h -> h.value?.let { v -> AllocationSlice(h.token, v, ((v.minorUnits * 10_000L) / total.minorUnits).toInt()) } }
            .sortedByDescending { it.value.minorUnits }
        if (slices.isEmpty()) return slices
        val remainder = 10_000 - slices.sumOf { it.fractionBps }
        return slices.mapIndexed { i, s -> if (i == 0) s.copy(fractionBps = s.fractionBps + remainder) else s }
    }
}
