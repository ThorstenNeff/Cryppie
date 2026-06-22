package com.tneff.cyppie.feature.strat

/**
 * KAN-166 UI-input + list models for Smart-Strategies (PRD-07b). The grant disclosure/sign now runs against
 * Dev-2's `:aa` `StrategyGrantService` / `StrategyGrantPreview` (KAN-165) — the interim stub is gone; only
 * these UI-side models remain.
 */

/** One target-allocation leg in the Setup UI: a basket [token] + its target [weightPercent] (weights sum to 100). */
data class BasketTarget(val token: String, val weightPercent: Int)

/**
 * One running strategy as a `Strat2-List` / `Strat3-Detail` row. Amounts are base-unit decimal Strings (FR-6).
 * (UI/list model — the backend strategy-sessions list endpoint is a follow; until then the list is empty.)
 */
data class StrategySession(
    val sessionId: String,
    val name: String,
    val budgetBaseUnits: String,
    val driftBps: Int,
    val status: String, // "active" | "paused"
    val lastRebalanceEpochSeconds: Long,
    val targets: List<BasketTarget>,
    // Strat3-Detail: the actual current allocation (vs [targets]) + a display-only performance string.
    val currentWeights: List<BasketTarget> = emptyList(),
    val performance: String? = null,
)

/** Float-free base-units → human amount via [decimals] (e.g. 30000000 @ 6 → "30"). Defensive on non-digits. */
internal fun humanAmount(base: String, decimals: Int): String {
    if (base.isEmpty() || base.any { it !in '0'..'9' }) return base
    val digits = base.trimStart('0').ifEmpty { "0" }
    if (decimals <= 0) return digits
    val padded = digits.padStart(decimals + 1, '0')
    val frac = padded.takeLast(decimals).trimEnd('0')
    val whole = padded.dropLast(decimals)
    return if (frac.isEmpty()) whole else "$whole.$frac"
}
