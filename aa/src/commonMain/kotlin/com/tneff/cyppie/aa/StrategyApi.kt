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
 * The app-built strategy scope (`/v1/strategy/session/prepare`, per `strategy-enable-scope-contract.md §2`).
 * [follower] is the app's OWN device-derived owner. [legs] = the per-token SELL-caps (EVERY basket token + the
 * budget token; cap = cumulative per-token) — the on-chain 🔒 set; [router] + [windowStart]/[windowEnd] complete
 * the scope. [weights] are **advisory** (the rebalance target — execution-time, NOT in the enable / permissionId);
 * carried only so the disclosure UI can render them visually separated.
 */
@Serializable
data class StrategyScopeRequest(
    val chainId: Long,
    val follower: String,
    val legs: List<StrategyCap>,
    val router: String,
    val windowStart: Long,
    val windowEnd: Long,
    val weights: List<StrategyWeight> = emptyList(),
)

/**
 * The prepared strategy session (backend `StrategyEnableInputs`). [caps] are the canonical per-token SELL-caps (the
 * 🔒 set; backend `sortLegs`-ordered, the app re-sorts identically); [permissionId] is the follow handle (app
 * cross-checks it against its own [StrategyEnableBuilder] permissionId — necessary-not-sufficient; the cap VALUES
 * are the real binding, checked in `verifyBasketGrant`). The app MUST cross-check [follower] against the device owner.
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
)

@Serializable
data class StrategyGrantRequest(val permissionId: String)
