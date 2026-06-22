package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import com.tneff.cyppie.evm.VerifiedBasketGrant
import com.tneff.cyppie.wallet.SeedSource

/**
 * Orchestrates the Smart-Strategy grant (PRD-07b Vaults-B, KAN-165) — the M-cap analogue of [FollowGrantService].
 * `prepare → StrategyEnableBuilder → verifyBasketGrant (disclosure) → EnableBroadcaster.broadcast → register`,
 * reusing the shared [EnableBroadcaster] (+ verifyEnableUserOp/verify7702/seed-hygiene) byte-identically to
 * Copy/DCA — only the session-config (M sell-caps) differs.
 *
 * 🔒 Two no-blind gates, exactly like Copy: (1) [prepareGrant] runs [SmartSessionGrantVerifier.verifyBasketGrant]
 * so the disclosure ([StrategyGrantPreview.verifiedGrant]) is derived from the signed-session bytes — the SELL-cap
 * set is 🔒, the weights are ℹ️ advisory; (2) [authorizeGrant] → [EnableBroadcaster] binds the owner's root sig to
 * the byte-exact enable. The account is the **device-derived owner**, never the backend's `prepare.follower`.
 */
class StrategyGrantService(
    private val api: StrategyApi,
    private val broadcaster: EnableBroadcaster = EnableBroadcaster(),
) {

    /** Phase 1 (disclosure): prepare, build the session on-device, self-verify it (no signing). */
    suspend fun prepareGrant(request: StrategyScopeRequest, owner: EvmAddress): StrategyGrantPreview {
        require(request.follower.equals(owner.value, ignoreCase = true)) {
            "scope.follower (${request.follower}) != device owner (${owner.value}) — refusing"
        }
        val p = api.prepare(request)
        require(p.follower.equals(owner.value, ignoreCase = true)) {
            "prepare.follower (${p.follower}) != device owner (${owner.value}) — refusing"
        }
        val ur = StrategyEnableBuilder.universalRouter(p.chainId)
            ?: throw IllegalArgumentException("unsupported chainId ${p.chainId}")
        val enable = StrategyEnableBuilder.build(
            chainId = p.chainId, account = owner.value, sessionPublicKey = p.sessionPublicKey,
            caps = p.caps, windowStart = p.windowStart, windowEnd = p.windowEnd, salt = p.salt, nonce = p.nonce,
        )
        // no-blind: the backend's permissionId must equal the session WE built from the disclosed caps.
        require(enable.permissionId.equals(p.permissionId, ignoreCase = true)) {
            "prepare.permissionId (${p.permissionId}) != on-device session (${enable.permissionId}) — refusing"
        }
        val verified = SmartSessionGrantVerifier.verifyBasketGrant(
            account = owner.value, chainId = p.chainId,
            sessionValidator = enable.sessionValidator, sessionValidatorInitData = enable.sessionValidatorInitData,
            salt = enable.salt, nonce = enable.nonce, permissions = enable.permissions, digestToSign = enable.digestToSign,
            swapTarget = ur, swapSelector = StrategyEnableBuilder.UNIVERSAL_ROUTER_EXECUTE_SELECTOR,
            expectedCaps = p.caps.associate { it.token to it.capBaseUnits }, // 🔒 the granted per-token cap VALUES (not just tokens)
            infraActions = listOf(SmartSessionGrantVerifier.ActionPin(StrategyEnableBuilder.PERMIT2, StrategyEnableBuilder.PERMIT2_APPROVE_SELECTOR)),
        )
        return StrategyGrantPreview(verified, request.weights, enable) // weights = advisory (UI input), not from prepare
    }

    /** Phase 2 (authorize): run the shared broadcaster (build→verify→owner-sign→submit→poll), then register. */
    suspend fun authorizeGrant(
        preview: StrategyGrantPreview,
        seedSource: SeedSource,
        maxPollAttempts: Int = 30,
        pollDelayMs: Long = 2_000,
    ): StrategyGrantResult {
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
        api.grantSession(StrategyGrantRequest(enable.permissionId)) // mark active only after a successful enable receipt
        return StrategyGrantResult(enable.permissionId, result.userOpHash, result.txHash)
    }
}

/**
 * The no-blind disclosure for the Strategy grant UI (KAN-166 seam). [verifiedGrant] is the **crypto-verified**
 * SELL-cap scope (🔒 — the per-token sell-caps + router + window); [weights] are **advisory** (ℹ️ — the rebalance
 * target, not on-chain-enforced) the UI must render visually separated.
 */
data class StrategyGrantPreview(
    val verifiedGrant: VerifiedBasketGrant,
    val weights: List<StrategyWeight>,
    val enable: BuiltStrategyEnable,
)

data class StrategyGrantResult(val permissionId: String, val userOpHash: String, val txHash: String?)
