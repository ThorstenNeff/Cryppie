package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.Quantity

/** A timestamped balance change for series reconstruction: [received] adds, else removes [amount]. */
data class TimedAmount(val epochSeconds: Long, val received: Boolean, val amount: Quantity)

/**
 * Builds the **value-over-time series** + **24h change** (KAN-102 P4). The series feeds
 * [PortfolioPerformance] (drawdown / Sharpe) and the chart; it is a best-effort approximation (FR-9) —
 * incomplete transfer history skews early points. 24h change is robust (current holdings × price
 * delta). All integer / float-free (FR-6).
 */
object PortfolioTimeSeries {

    /**
     * Value series for one token: at each [history] price point, the **cumulative net holding**
     * reconstructed from [transfers] (received − sent up to that timestamp) valued at that price.
     */
    fun tokenValueSeries(decimals: Int, transfers: List<TimedAmount>, history: List<PricePoint>): List<ValuePoint> {
        val sorted = transfers.sortedBy { it.epochSeconds }
        return history.sortedBy { it.epochSeconds }.map { point ->
            var net = Quantity.ZERO
            for (t in sorted) {
                if (t.epochSeconds > point.epochSeconds) break
                net = when {
                    t.received -> net + t.amount
                    net >= t.amount -> net - t.amount
                    else -> Quantity.ZERO // disposed more than tracked (incomplete history)
                }
            }
            ValuePoint(
                point.epochSeconds,
                Money(Valuation.valueCents(net, decimals, point.price), Valuation.MONEY_SCALE, point.price.currency),
            )
        }
    }

    /**
     * Portfolio 24h change = Σ over holdings of currentHolding × (priceNow − price24hAgo). Robust
     * (current holdings, just a price delta). Holdings missing either price are skipped. [currency] is
     * the display fiat.
     */
    fun change24h(
        holdings: List<Holding>,
        pricesNow: Map<PortfolioToken, Money>,
        prices24hAgo: Map<PortfolioToken, Money>,
        currency: String,
    ): Metric<Money> {
        var deltaCents = 0L
        for (h in holdings) {
            val now = pricesNow[h.token] ?: continue
            val ago = prices24hAgo[h.token] ?: continue
            val change = saturatingSub(
                Valuation.valueCents(h.rawBalance, h.token.decimals, now),
                Valuation.valueCents(h.rawBalance, h.token.decimals, ago),
            )
            deltaCents = saturatingAdd(deltaCents, change)
        }
        return Metric.robust(Money(deltaCents, Valuation.MONEY_SCALE, currency))
    }

    private fun saturatingAdd(a: Long, b: Long): Long {
        val r = a + b
        return if ((a xor r) and (b xor r) < 0L) (if (a > 0L) Long.MAX_VALUE else Long.MIN_VALUE) else r
    }

    private fun saturatingSub(a: Long, b: Long): Long {
        val r = a - b
        return if ((a xor b) and (a xor r) < 0L) (if (a >= 0L) Long.MAX_VALUE else Long.MIN_VALUE) else r
    }
}
