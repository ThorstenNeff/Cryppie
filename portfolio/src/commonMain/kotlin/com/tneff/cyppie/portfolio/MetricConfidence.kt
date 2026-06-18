package com.tneff.cyppie.portfolio

/**
 * FR-9 accuracy flag for a portfolio metric. Metrics computed from current state (total value, 24h
 * change, allocation) are [Robust]; metrics reconstructed from incomplete history (P&L, Sharpe,
 * max-drawdown, the value-over-time series) are [Approximate] — the UI must surface a visible "≈"
 * caveat + the [reasons]. Canonical values land later via the backend analytics (PRD-08, soft).
 */
sealed interface MetricConfidence {
    data object Robust : MetricConfidence
    data class Approximate(val reasons: List<ApproxReason>) : MetricConfidence
}

/** Why a metric is only an approximation — each maps to a short, user-facing caveat string in the UI. */
enum class ApproxReason {
    COST_BASIS_AMBIGUITY, // FIFO assumption; no user-confirmed cost basis
    INCOMPLETE_TRANSFERS, // missing / gapped transfer history
    UNVERIFIED_TOKENS, // spam / unpriced tokens excluded or unpriced
    WALLET_PREDATES_TRACKING, // holdings acquired before the tracked window
    STALE_PRICES, // a price was older than the freshness window
}

/** A portfolio metric carrying its FR-9 confidence so the value and its caveat never get separated. */
data class Metric<out T>(val value: T, val confidence: MetricConfidence) {

    val isApproximate: Boolean get() = confidence is MetricConfidence.Approximate

    /** Reasons if approximate, else empty. */
    val reasons: List<ApproxReason>
        get() = (confidence as? MetricConfidence.Approximate)?.reasons.orEmpty()

    fun <R> map(transform: (T) -> R): Metric<R> = Metric(transform(value), confidence)

    companion object {
        fun <T> robust(value: T): Metric<T> = Metric(value, MetricConfidence.Robust)

        fun <T> approximate(value: T, reasons: List<ApproxReason>): Metric<T> =
            Metric(value, MetricConfidence.Approximate(reasons.distinct()))

        fun <T> approximate(value: T, vararg reasons: ApproxReason): Metric<T> =
            approximate(value, reasons.toList())
    }
}
