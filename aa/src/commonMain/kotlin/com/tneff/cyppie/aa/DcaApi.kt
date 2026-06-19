package com.tneff.cyppie.aa

import kotlinx.serialization.Serializable

/**
 * The app-facing DCA / AA API — the app talks **only** to the JWT User-Service (never the loopback
 * `aa-trigger`, which the User-Service calls internally; Ph0 §3/§4). The exact `/v1/me/...` paths +
 * shapes are owned by the backend (Ph1) — these mirror §4/§5 and are reconciled once the backend
 * publishes the User-Service surface. All amounts are base-unit decimal Strings (FR-6).
 */
interface DcaApi {
    /** Build the session-enable owner-userOp for [config]; returns the 32-byte digest the app signs
     *  on-device (Ph0 §4 — session-enable is an owner-userOp, so the app just signs its hash). */
    suspend fun buildSessionEnable(config: SessionConfig): SessionEnable

    /** Register an on-device-enabled Smart Session (grant UX → §2 config + the enable signature). */
    suspend fun grantSession(config: SessionConfig, enableSignature: String): GrantResult

    /** The user's active sessions (running DCA schedules) for the management/revoke UI. */
    suspend fun listSessions(): List<SessionConfig>

    /** Revoke a session (on-chain disable + registry removal). Always reachable (kill-switch per session). */
    suspend fun revokeSession(sessionId: String)

    /** DCA ops awaiting the user's on-device signature (foreground-poll for MVP; push is a fast-follow). */
    suspend fun pendingDca(): List<PendingDca>

    /** Submit the on-device signature for a pending DCA op (§4 step 3 → `/v1/me/dca/{id}/signature`). */
    suspend fun submitSignature(dcaId: String, signature: String)

    /** Op status / receipt (§3 `GET /v1/userop/{chainId}/{hash}`, surfaced via the User-Service). */
    suspend fun opStatus(chainId: Long, userOpHash: String): OpStatus

    /** Global kill-switch state (backend pause flag); when paused, no op can be signed/submitted. */
    suspend fun aaStatus(): AaStatus
}

/** The session-enable digest the app signs on-device to authorize a new Smart Session. */
@Serializable
data class SessionEnable(val digestToSign: String, val validUntil: Long = 0)

@Serializable
data class GrantResult(val sessionId: String)

/** A DCA op the backend built (unsigned) and is waiting for the on-device signature over [userOpHash]. */
@Serializable
data class PendingDca(
    val id: String,
    val chainId: Long,
    val account: String,
    val userOpHash: String,        // 0x.. — the 32-byte hash the app signs on-device
    val action: DcaAction,
    val validUntil: Long,
)

@Serializable
data class DcaAction(
    val tokenIn: String,
    val tokenOut: String,
    val amountIn: String,          // base units (decimal String)
    val router: String,
    val minOut: String = "0",
)

@Serializable
data class OpStatus(val status: String, val txHash: String? = null) // pending | included | failed

@Serializable
data class AaStatus(val paused: Boolean)
