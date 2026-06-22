package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.BuyUserOpVerifier
import kotlinx.serialization.Serializable

/**
 * The app-facing DCA / AA API — the app talks **only** to the JWT User-Service (never the loopback
 * `aa-trigger`, which the User-Service calls internally; Ph0 §3/§4). The exact `/v1/me/...` paths +
 * shapes are owned by the backend (Ph1) — these mirror §4/§5 and are reconciled once the backend
 * publishes the User-Service surface. All amounts are base-unit decimal Strings (FR-6).
 */
interface DcaApi : EnableBroadcastApi {
    // The session-config is built ENTIRELY on-device ([DcaEnableBuilder]); the enable userOp itself is built +
    // submitted via the SHARED generic [EnableBroadcastApi] endpoints (`/v1/userop/build` + `/submit`, KAN-159) —
    // the same Kernel install+enableSessions batch as Copy, only the enableSessions(session) calldata differs.
    // The app verifies (verifyEnableUserOp + verify7702Authorization) + owner-signs, then registers below.

    /**
     * Register an on-device-enabled Smart Session (grant UX → §2 config). KAN-159: the enable is now
     * broadcast on-chain via [EnableBroadcastApi] BEFORE this call, so the old `enableSignature` arg is dead
     * data (cf. backend `copy-trading-c6-execution`: "enableSignature removed; grant just marks active") —
     * registration carries the §2 schedule (off-chain frequency/usage metadata) keyed by the now-enabled session.
     */
    suspend fun grantSession(config: SessionConfig): GrantResult

    /** The user's active sessions (running DCA schedules) for the management/revoke UI. */
    suspend fun listSessions(): List<SessionConfig>

    /** Revoke a session (on-chain disable + registry removal). Always reachable (kill-switch per session). */
    suspend fun revokeSession(sessionId: String)

    /** Create a recurring DCA schedule under an enabled session (one-time setup, KAN-163 `POST /v1/me/dca/schedules`). */
    suspend fun createSchedule(request: DcaScheduleRequest): DcaScheduleResult

    /** Built, unsigned recurring buys awaiting the on-device signature (foreground-poll MVP; `GET /v1/me/dca/pending`). */
    suspend fun pendingDca(): List<PendingDca>

    /**
     * Submit the on-device RAW signature for a pending buy (KAN-163 `POST /v1/me/dca/{id}/submit`); the backend
     * re-gates (kill-switch/Q7), relays the parked userOp + signature, records the spend, and returns the
     * bundler userOpHash (poll the receipt via [opStatus]).
     */
    suspend fun submitSignature(dcaId: String, signature: String): String

    // opStatus is inherited from EnableBroadcastApi (`GET /v1/userop/{chainId}/{hash}`).

    /** Global kill-switch state (backend pause flag); when paused, no op can be signed/submitted. */
    suspend fun aaStatus(): AaStatus
}

@Serializable
data class GrantResult(val sessionId: String)

/**
 * A built, unsigned recurring DCA buy the scheduler parked (KAN-163). The app raw-signs [digestToSign]
 * (== [userOpHash], the **C3 USE-lock RAW** form — NOT the EIP-191 `hashMessage` the *enable* path uses)
 * on-device and submits the signature; the opaque userOp itself stays backend-side. [tokenIn]/[amountIn] are
 * the disclosed spend for this buy (advisory display). The app refuses to sign if [digestToSign] != [userOpHash].
 */
@Serializable
data class PendingDca(
    val id: String,
    val chainId: Long,
    val account: String,
    val tokenIn: String,           // the spend token for this buy (advisory)
    val amountIn: String,          // base units, decimal String (advisory)
    val userOpHash: String,        // 0x.. — the 32-byte hash the backend built
    val digestToSign: String,      // == userOpHash (RAW); fail-closed if it differs (C3 raw-lock)
    // KAN-163 (b): the full built v0.7 userOp (backend `307ed12`) — the app RECOMPUTES the userOpHash from
    // this + binds it to digestToSign + scope-checks the calls ([verifyDcaBuy]) before signing (true no-blind).
    val userOp: UnpackedUserOp,
)

/**
 * KAN-163 (b) NO-BLIND: recompute the buy userOpHash from the parked [PendingDca.userOp], bind it to
 * [PendingDca.digestToSign], and scope-check the calls — EXACTLY approve([spendToken]→[router]) + a swap on
 * [router]/[swapSelector], sender==[expectedAccount], initCode==0x ([BuyUserOpVerifier], byte-pinned vs the
 * backend buy vector). Throws [com.tneff.cyppie.evm.BuyVerificationException] on ANY mismatch — the app must
 * NOT sign (fail-closed). The C3 RAW-lock (raw userOpHash, never EIP-191) is enforced inside the verifier.
 */
fun verifyDcaBuy(
    pending: PendingDca,
    expectedAccount: String,
    spendToken: String,
    expectedTokenOut: String,
    router: String,
    swapSelector: String,
) {
    BuyUserOpVerifier.verify(
        userOp = pending.userOp.toPackedUserOp(),
        digestToSign = pending.digestToSign,
        chainId = pending.chainId,
        expectedAccount = expectedAccount,
        spendToken = spendToken,
        // 🔒 Bind the swap OUTPUT to the token the USER consented at grant (dcaGrantParams.buyToken), never a
        // backend-supplied pending value (that would be circular). Decodes the inner swap; asserts ==. Fail-closed.
        tokenOut = expectedTokenOut,
        router = router,
        swapSelector = swapSelector,
    )
}

/**
 * One-time DCA schedule setup (KAN-163, `POST /v1/me/dca/schedules`) under an enabled session
 * ([permissionId]); the backend scheduler then builds this schedule's buys each tick. uint256s are decimal
 * Strings; [intervalSeconds] ≥ 3600.
 */
@Serializable
data class DcaScheduleRequest(
    val chainId: Long,
    val account: String,
    val tokenIn: String,
    val tokenOut: String,
    val amountIn: String,
    val router: String,
    val intervalSeconds: Long,
    val permissionId: String,
    val feeTier: Int,
    val amountOutMin: String? = null,
)

@Serializable
data class DcaScheduleResult(val scheduleId: String)

@Serializable
data class OpStatus(val status: String, val txHash: String? = null) // pending | included | failed

@Serializable
data class AaStatus(val paused: Boolean)
