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
     * Portfolio 24h change = Σ over holdings of currentHolding × (priceNow − price24hAgo). Robust when
     * every holding had both prices (just a price delta on current holdings); if any holding is dropped
     * for want of a now/24h-ago price it would silently understate the total, so the result is then
     * Approximate ([ApproxReason.INCOMPLETE_PRICE_DATA]) — never a "safe-looking" number that omits
     * tokens (FR-9). [currency] is the display fiat.
     */
    fun change24h(
        holdings: List<Holding>,
        pricesNow: Map<PortfolioToken, Money>,
        prices24hAgo: Map<PortfolioToken, Money>,
        currency: String,
    ): Metric<Money> {
        var deltaCents = 0L
        var skipped = 0
        for (h in holdings) {
            val now = pricesNow[h.token]
            val ago = prices24hAgo[h.token]
            if (now == null || ago == null) {
                skipped++
                continue
            }
            val change = saturatingSub(
                Valuation.valueCents(h.rawBalance, h.token.decimals, now),
                Valuation.valueCents(h.rawBalance, h.token.decimals, ago),
            )
            deltaCents = saturatingAdd(deltaCents, change)
        }
        val money = Money(deltaCents, Valuation.MONEY_SCALE, currency)
        return if (skipped > 0) Metric.approximate(money, ApproxReason.INCOMPLETE_PRICE_DATA) else Metric.robust(money)
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
