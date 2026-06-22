package com.tneff.cyppie.aa

import kotlinx.serialization.Serializable

/**
 * The app-facing Smart-Strategy API (PRD-07b Vaults-B, KAN-165) — the M-cap analogue of [CopyApi]. The build/submit
 * surface is the shared [EnableBroadcastApi]; this adds the strategy-specific prepare + grant. **Provisional shapes
 * — reconcile the exact JSON with the backend's KAN-164 strategy-engine contract** (the backend owns the canonical
 * session-config = per-token sell-caps on the user's basket+budget tokens; the app matches + verifies on-device).
 */
interface StrategyApi : EnableBroadcastApi {

    /** Prepare the session for a target-allocation strategy from the app-supplied [StrategyScopeRequest]. */
    suspend fun prepare(request: StrategyScopeRequest): StrategyPrepare

    /** Mark the strategy active after a successful enable receipt. */
    suspend fun grantSession(request: StrategyGrantRequest)
}

/** A target-allocation weight (advisory — the rebalance target, NOT on-chain-enforced). */
@Serializable
data class StrategyWeight(val token: String, val weightBps: Int)

/**
 * The app-built strategy scope (from the UI's basket + budget). [follower] is the app's OWN device-derived owner.
 * The [basket] tokens + [budgetToken] define the on-chain SELL set (caps go on these); the [weights] are advisory.
 */
@Serializable
data class StrategyScopeRequest(
    val chainId: Long,
    val follower: String,
    val budgetToken: String,
    val budget: String,
    val basket: List<StrategyWeight>,
    val windowStart: Long,
    val windowEnd: Long,
)

/**
 * The prepared strategy session (backend `EnableInputs`). [caps] are the backend-computed per-token SELL-caps over
 * the basket+budget tokens (the 🔒 set); [permissionId] is the follow handle (app cross-checks it against its own
 * [StrategyEnableBuilder] permissionId). [weights] are advisory (echoed for the disclosure). The app MUST cross-
 * check [follower] against the device owner.
 */
@Serializable
data class StrategyPrepare(
    val permissionId: String,
    val chainId: Long,
    val follower: String,
    val sessionPublicKey: String,
    val caps: List<StrategyCap>,
    val windowStart: Long,
    val windowEnd: Long,
    val salt: String,
    val nonce: Long,
    val weights: List<StrategyWeight> = emptyList(),
)

@Serializable
data class StrategyGrantRequest(val permissionId: String)
