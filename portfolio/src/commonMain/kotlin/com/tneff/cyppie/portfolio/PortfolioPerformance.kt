package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money

/** A point in the portfolio value-over-time series (KAN-100 P4). The series itself is reconstructed. */
data class ValuePoint(val epochSeconds: Long, val value: Money)

/**
 * On-device **best-effort** performance metrics (FR-9). Every result is [Metric.approximate] — the
 * inputs (holdings-over-time + FIFO cost basis) are reconstructed from incomplete transfer history, so
 * the canonical values come later from the backend (PRD-08). The formulas themselves are deterministic
 * and reference-checkable (FR-6); no floating point (fixed-point / integer math).
 */
object PortfolioPerformance {

    /** Unrealized P&L = current value − cost basis (same currency + scale). Always approximate. */
    fun unrealizedPnl(
        currentValue: Money,
        costBasis: Money,
        extraReasons: List<ApproxReason> = emptyList(),
    ): Metric<Money> {
        require(currentValue.currency == costBasis.currency) { "currency mismatch" }
        require(currentValue.scale == costBasis.scale) { "scale mismatch" }
        // Saturating subtract (KAN-102 L1, same family as KAN-98-L1): capped/filtered-absurd inputs
        // must never wrap a P&L figure.
        val pnl = Money(saturatingSub(currentValue.minorUnits, costBasis.minorUnits), currentValue.scale, currentValue.currency)
        return Metric.approximate(pnl, listOf(ApproxReason.COST_BASIS_AMBIGUITY) + extraReasons)
    }

    /**
     * Maximum drawdown over [series], in basis points of the running peak (0..10_000). A flat or rising
     * series → 0. Approximate (reconstructed series; wallet may predate tracking).
     */
    fun maxDrawdownBps(series: List<ValuePoint>): Metric<Int> {
        var peak = Long.MIN_VALUE
        var maxDd = 0
        for (p in series) {
            val v = p.value.minorUnits
            if (v > peak) peak = v
            if (peak > 0 && v < peak) {
                val dd = ratioBps(peak - v, peak).toInt()
                if (dd > maxDd) maxDd = dd
            }
        }
        return Metric.approximate(maxDd, ApproxReason.INCOMPLETE_TRANSFERS, ApproxReason.WALLET_PREDATES_TRACKING)
    }

    /**
     * Sharpe-like ratio over the period returns of [series], as a fixed-point value × 1000 (risk-free =
     * 0, not annualised — MVP). Null if fewer than 3 points. Integer math (returns in bps, integer
     * std-dev). Approximate.
     */
    fun sharpeMilli(series: List<ValuePoint>): Metric<Long>? {
        if (series.size < 3) return null
        val returnsBps = (1 until series.size).mapNotNull { i ->
            val prev = series[i - 1].value.minorUnits
            if (prev <= 0L) null else ratioBps(series[i].value.minorUnits - prev, prev)
        }
        if (returnsBps.size < 2) return null
        val mean = returnsBps.sum() / returnsBps.size
        val variance = returnsBps.sumOf { val d = it - mean; d * d } / returnsBps.size
        val stddev = isqrt(variance)
        val sharpe = if (stddev == 0L) 0L else (mean * 1_000L) / stddev
        return Metric.approximate(sharpe, ApproxReason.INCOMPLETE_TRANSFERS, ApproxReason.COST_BASIS_AMBIGUITY)
    }

    /** Saturating subtract — clamps to Long.MIN/MAX instead of wrapping (KAN-102 L1). */
    private fun saturatingSub(a: Long, b: Long): Long {
        val r = a - b
        return if ((a xor b) and (a xor r) < 0L) (if (a >= 0L) Long.MAX_VALUE else Long.MIN_VALUE) else r
    }

    /**
     * [delta] / [base] in basis points, **overflow-safe + float-free** (KAN-102 L1): for bounded real
     * inputs takes the exact `delta×10_000/base`; for huge (capped) inputs divides first to avoid the
     * `×10_000` overflow, trading a little precision for safety. Sign preserved; base ≤ 0 → 0.
     */
    private fun ratioBps(delta: Long, base: Long): Long {
        if (base <= 0L) return 0L
        val ad = if (delta < 0L) -delta else delta
        val sign = if (delta < 0L) -1L else 1L
        val mag = if (ad <= Long.MAX_VALUE / 10_000L) ad * 10_000L / base else ad / (base / 10_000L).coerceAtLeast(1L)
        return sign * mag
    }

    /** Integer square root (Newton's method) — keeps the std-dev float-free. */
    private fun isqrt(n: Long): Long {
        if (n <= 0L) return 0L
        var x = n
        var y = (x + 1) / 2
        while (y < x) {
            x = y
            y = (x + n / x) / 2
        }
        return x
    }
}
