package com.tneff.cyppie.feature.strat

import com.tneff.cyppie.wallet.SeedSource

/**
 * KAN-166 scaffold seam types for Smart-Strategies (PRD-07b). These mirror the shape the `:aa` strategy
 * grant/crypto (Dev-2, KAN-165) will provide; the UI is built against them + a [StubStrategyGrantService]
 * so the screens/flow exist now, and the real verify→sign→submit service is wired in later (like Copy did
 * with its interim stub → Dev-2's FollowGrantService).
 */

/** One target-allocation leg: a basket [token] + its target [weightPercent] (the weights sum to 100). */
data class BasketTarget(val token: String, val weightPercent: Int)

/**
 * The no-blind, **sell-side** disclosure for a strategy grant (the Copy-trust pattern, KAN-167 refinement).
 * The 🔒 verified facts are the SELL side — the strategy may sell at most [sellCapBaseUnits] of the on-chain
 * pinned [basketTokens] via [router]/[actionSelector] within [windowStartEpochSeconds]..[windowEndEpochSeconds].
 * [targets] are **advisory** (mirror-time target weights / buy direction — not in the enable digest).
 */
data class StrategyGrantPreview(
    val sellCapBaseUnits: String,
    val basketTokens: List<String>,
    val router: String,
    val actionSelector: String,
    val windowStartEpochSeconds: Long,
    val windowEndEpochSeconds: Long,
    val targets: List<BasketTarget>,
)

/** One running strategy as a `Strat2-List` row. Amounts are base-unit decimal Strings (FR-6). */
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

/**
 * Scaffold stand-in for Dev-2's KAN-165 `:aa` strategy service. Builds a deterministic [StrategyGrantPreview]
 * from the UI inputs (no real crypto — the on-device verify→sign lands with KAN-165) and exposes empty
 * management lists. The app shell binds the [StrategyViewModel]/[StrategyListViewModel] seams to this until
 * the real service replaces it (the lambda-seam VMs don't change).
 */
class StubStrategyGrantService(
    private val router: String,
    private val actionSelector: String,
    private val windowSeconds: Long,
    private val nowEpochSeconds: () -> Long,
) {
    /** Scaffold: sell-cap = the budget cap; basket = the chosen tokens; window = now..now+windowSeconds. */
    suspend fun prepareGrant(targets: List<BasketTarget>, budgetBaseUnits: String): StrategyGrantPreview {
        val now = nowEpochSeconds()
        return StrategyGrantPreview(
            sellCapBaseUnits = budgetBaseUnits,
            basketTokens = targets.map { it.token },
            router = router,
            actionSelector = actionSelector,
            windowStartEpochSeconds = now,
            windowEndEpochSeconds = now + windowSeconds,
            targets = targets,
        )
    }

    /** Scaffold no-op — KAN-165 replaces this with verify→owner-sign→submit (broadcaster owns the seed). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun authorizeGrant(preview: StrategyGrantPreview, seed: SeedSource) { /* no real sign yet (KAN-165) */ }

    /** Scaffold: no backend yet → no active strategies. KAN-165/engine fills this (mirrors GET /v1/copy/sessions). */
    suspend fun listStrategies(): List<StrategySession> = emptyList()

    /** Scaffold no-op — KAN-165 replaces with the no-blind RevokeBroadcaster path (like Copy revoke). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun revoke(session: StrategySession, seed: SeedSource) { /* no real revoke yet (KAN-165) */ }

    /** Scaffold no-op — the instant off-chain kill-switch (pause/resume), no sign. Backend wires it later. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun setPaused(session: StrategySession, paused: Boolean) { /* no backend yet */ }
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
