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

/** Float-free sum of non-negative base-10 strings (for the ≈value envelope total). Defensive → "0" on non-digits. */
internal fun sumDecimal(values: List<String>): String {
    var acc = "0"
    for (raw in values) {
        val a = acc; val b = raw.trimStart('0').ifEmpty { "0" }
        if (a.any { it !in '0'..'9' } || b.any { it !in '0'..'9' }) return "0"
        val out = StringBuilder(); var i = a.length - 1; var j = b.length - 1; var carry = 0
        while (i >= 0 || j >= 0 || carry > 0) {
            val s = (if (i >= 0) a[i--] - '0' else 0) + (if (j >= 0) b[j--] - '0' else 0) + carry
            out.append(('0' + s % 10)); carry = s / 10
        }
        acc = out.reverse().toString().trimStart('0').ifEmpty { "0" }
    }
    return acc
}

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
