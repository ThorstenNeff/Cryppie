package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import com.tneff.cyppie.evm.VerifiedGrant
import com.tneff.cyppie.wallet.SeedSource

/**
 * Orchestrates the Copy-trading grant (PRD-06, KAN-154) end-to-end on the app side: prepare the scope, build +
 * **defensively verify** the session disclosure on-device, then — at the user's authorization — run the shared
 * [EnableBroadcaster] (build → verifyEnableUserOp → 7702-pin → owner-sign → submit → poll) and mark the session
 * granted. The broadcaster is reused verbatim for DCA (KAN-159); here it is wrapped in the Copy scope.
 *
 * 🔒 Two independent no-blind gates: (1) [prepareGrant] runs [SmartSessionGrantVerifier.verifyGrant] so the
 * disclosure ([CopyGrantPreview.verifiedGrant]) is derived from the signed-session bytes; (2) [authorizeGrant] →
 * [EnableBroadcaster] runs [com.tneff.cyppie.evm.EnableUserOpVerifier] so the owner's ROOT-validator signature is
 * bound to an op that enables EXACTLY that session — a tampered cap/router/window/owner (or an unrelated op) fails
 * closed before signing. The account is the **device-derived owner** (caller-supplied), never `prepare.follower`.
 */
class FollowGrantService(
    private val api: CopyApi,
    private val broadcaster: EnableBroadcaster = EnableBroadcaster(),
) {

    /**
     * Phase 1 (disclosure): fetch the scope, build the session on-device, and self-verify it. [owner] is the
     * device-derived owner EOA; we refuse if the backend's `follower` does not match it (no backend-account trust).
     * Returns the preview for the no-blind UI; performs NO signing.
     */
    suspend fun prepareGrant(request: CopyScopeRequest, owner: EvmAddress): CopyGrantPreview {
        require(request.follower.equals(owner.value, ignoreCase = true)) {
            "scope.follower (${request.follower}) != device owner (${owner.value}) — refusing"
        }
        val p = api.prepare(request)
        require(p.follower.equals(owner.value, ignoreCase = true)) {
            "prepare.follower (${p.follower}) != device owner (${owner.value}) — refusing"
        }
        val ur = CopyEnableBuilder.universalRouter(p.chainId)
            ?: throw IllegalArgumentException("unsupported chainId ${p.chainId}")
        val enable = CopyEnableBuilder.build(
            chainId = p.chainId, follower = owner.value, sessionPublicKey = p.sessionPublicKey,
            spendToken = p.token, capBaseUnits = p.capTotalBudget,
            windowStart = p.windowStart, windowEnd = p.windowEnd, salt = p.salt, nonce = p.nonce,
        )
        // no-blind: the backend's permissionId must equal the session WE built from the disclosed scope.
        require(enable.permissionId.equals(p.permissionId, ignoreCase = true)) {
            "prepare.permissionId (${p.permissionId}) != on-device session (${enable.permissionId}) — refusing"
        }
        val verified = SmartSessionGrantVerifier.verifyGrant(
            account = owner.value, chainId = p.chainId,
            sessionValidator = enable.sessionValidator, sessionValidatorInitData = enable.sessionValidatorInitData,
            salt = enable.salt, nonce = enable.nonce, permissions = enable.permissions, digestToSign = enable.digestToSign,
            swapTarget = ur, swapSelector = CopyEnableBuilder.UNIVERSAL_ROUTER_EXECUTE_SELECTOR,
            infraActions = listOf(SmartSessionGrantVerifier.ActionPin(CopyEnableBuilder.PERMIT2, CopyEnableBuilder.PERMIT2_APPROVE_SELECTOR)),
        )
        return CopyGrantPreview(verified, p.source, p.allocationBps ?: 0, enable)
    }

    /**
     * Phase 2 (authorize): build the enable userOp, re-verify it byte-exact, owner-sign the bound digest with the
     * re-auth [seedSource] (zeroized after), submit, poll for inclusion, then mark the session granted. Throws if
     * verification fails (do NOT sign) or the op reverts / never includes.
     */
    suspend fun authorizeGrant(
        preview: CopyGrantPreview,
        seedSource: SeedSource,
        maxPollAttempts: Int = 30,
        pollDelayMs: Long = 2_000,
    ): CopyGrantResult {
        val enable = preview.enable
        val result = broadcaster.broadcast(
            api = api,
            expected = ExpectedEnable(
                chainId = enable.chainId, account = enable.account, permissionId = enable.permissionId,
                sessionValidator = enable.sessionValidator, sessionValidatorInitData = enable.sessionValidatorInitData,
                salt = enable.salt, permissions = enable.permissions,
            ),
            seedSource = seedSource, maxPollAttempts = maxPollAttempts, pollDelayMs = pollDelayMs,
        )
        api.grantSession(CopyGrantRequest(enable.permissionId)) // mark active only after a successful enable receipt
        return CopyGrantResult(enable.permissionId, result.userOpHash, result.txHash)
    }
}

/**
 * The no-blind disclosure for the Copy grant UI (KAN-155 seam). [verifiedGrant] is the **crypto-verified** scope
 * (cap/token/router/window) Dev-1 renders as the authorization; [source]/[allocationBps] are **advisory** context
 * (not in the signed enable) Dev-1 must render visually separated, never as part of the on-chain guarantee.
 */
data class CopyGrantPreview(
    val verifiedGrant: VerifiedGrant,
    val source: String,
    val allocationBps: Int,
    val enable: BuiltCopyEnable,
)

data class CopyGrantResult(val permissionId: String, val userOpHash: String, val txHash: String?)
