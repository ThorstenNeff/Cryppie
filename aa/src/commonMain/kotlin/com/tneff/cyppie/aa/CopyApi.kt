package com.tneff.cyppie.aa

import kotlinx.serialization.Serializable

/**
 * The app-facing Copy-trading API (PRD-06, KAN-154) — the app talks ONLY to the JWT User-Service. Copy's enable
 * is an **owner-signed enable userOp** the backend builds + submits via Pimlico (GAP-B / Approach B): the app
 * `prepare`s the scope, rebuilds + verifies the op on-device ([com.tneff.cyppie.evm.EnableUserOpVerifier]),
 * owner-signs the `digestToSign`, and the backend submits. The build/submit/poll surface is the shared
 * [EnableBroadcastApi] (reused by DCA, KAN-159); this interface adds the Copy-specific prepare + grant. Paths
 * mirror `docs/copy-trading-enable-submit-contract.md` and reconcile with the backend surface.
 */
interface CopyApi : EnableBroadcastApi {

    /**
     * Create/prepare the session for a follow relationship from the app-supplied [CopyScopeRequest] (trader +
     * budget + chain from the UI's pick-trader/set-budget steps); the backend assigns the session key, salt and
     * nonce and returns the full [CopyPrepare]. There is no separate create-follow step — `prepare` is it
     * (`POST /v1/copy/session/prepare`).
     */
    suspend fun prepare(request: CopyScopeRequest): CopyPrepare

    /** Mark the session active after a successful enable receipt (`/v1/copy/session/grant`). */
    suspend fun grantSession(request: CopyGrantRequest)
}

/**
 * The app-built scope for a follow (from the UI's pick-trader + set-budget steps). [follower] is the app's OWN
 * device-derived owner (the app supplies it, never reads it from the backend); [source] is the followed trader.
 * [router]/[selector] are the client-pinned UniversalRouter + execute selector; [tokenOut]/[feeTier]/[slippageBps]
 * are **mirror-time** params (NOT in the enable digest → permissionId unchanged). There is no separate follow id —
 * the prepared session's [CopyPrepare.permissionId] IS the follow handle.
 *
 * NB [tokenOut]/[feeTier] are nullable for the user-selectable mirror-token mode (KAN-161): "fixed" → the user
 * pre-selects them (sent); "dynamic" → the webhook derives tokenOut from the detected trade (omitted). Either way
 * they do NOT affect the enable digest / permissionId, so the crypto path + KAT are unchanged.
 */
@Serializable
data class CopyScopeRequest(
    val chainId: Long,
    val source: String,
    val capTotalBudget: String,
    val token: String,
    val follower: String,
    val router: String,
    val selector: String,
    val windowStart: Long,
    val windowEnd: Long,
    val tokenOut: String? = null,
    val feeTier: Int? = null,
    val allocationBps: Int? = null,
    val slippageBps: Int? = null,
)

/**
 * The prepared session the app will enable (backend `EnableInputs`). [follower] is the SCA (= device owner EOA,
 * 7702 same-address) — the app MUST cross-check it against the device-derived owner and refuse if it differs.
 * [permissionId] is the backend's notion of the session (= the follow handle); the app cross-checks it against
 * its own on-device [CopyEnableBuilder] permissionId. [sessionPublicKey]/[salt]/[nonce] are backend-assigned.
 */
@Serializable
data class CopyPrepare(
    val permissionId: String,
    val chainId: Long,
    val follower: String,
    val sessionPublicKey: String,
    val token: String,
    val capTotalBudget: String,
    val windowStart: Long,
    val windowEnd: Long,
    val salt: String,
    val nonce: Long,
    val source: String,
    val allocationBps: Int? = null,
)

@Serializable
data class CopyGrantRequest(val permissionId: String)
