package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.Eip7702Authorization
import com.tneff.cyppie.evm.EnableUserOpVerifier
import com.tneff.cyppie.evm.Erc4337UserOp
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import com.tneff.cyppie.evm.VerifiedGrant
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.delay

/**
 * Orchestrates the Copy-trading grant (PRD-06, KAN-154) end-to-end on the app side: prepare the scope, build +
 * **defensively verify** the session disclosure on-device, then — at the user's authorization — build the enable
 * userOp via the backend, **re-verify it byte-exact** ([EnableUserOpVerifier]), owner-sign the bound digest, and
 * submit it for the backend to broadcast (GAP-B / Approach B). The same enable-broadcast path is reused for DCA
 * (KAN-159) via [EnableUserOpVerifier]; here it is wrapped in the Copy scope.
 *
 * 🔒 Two independent no-blind gates: (1) [prepareGrant] runs [SmartSessionGrantVerifier.verifyGrant] so the
 * disclosure ([CopyGrantPreview.verifiedGrant]) is derived from the signed-session bytes; (2) [authorizeGrant]
 * runs [EnableUserOpVerifier.verify] so the owner's ROOT-validator signature is bound to an op that enables
 * EXACTLY that session — a tampered cap/router/window/owner (or an unrelated op) fails closed before signing.
 * The account is the **device-derived owner** (caller-supplied), never the backend's `prepare.follower`.
 */
class FollowGrantService(
    private val api: CopyApi,
    private val aaSigner: AaSigner = AaSigner(),
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
            spendToken = p.spendToken, capBaseUnits = p.capBaseUnits,
            windowStart = p.windowStart, windowEnd = p.windowEnd, salt = p.salt, nonce = p.nonce,
        )
        val verified = SmartSessionGrantVerifier.verifyGrant(
            account = owner.value, chainId = p.chainId,
            sessionValidator = enable.sessionValidator, sessionValidatorInitData = enable.sessionValidatorInitData,
            salt = enable.salt, nonce = enable.nonce, permissions = enable.permissions, digestToSign = enable.digestToSign,
            swapTarget = ur, swapSelector = CopyEnableBuilder.UNIVERSAL_ROUTER_EXECUTE_SELECTOR,
            infraActions = listOf(SmartSessionGrantVerifier.ActionPin(CopyEnableBuilder.PERMIT2, CopyEnableBuilder.PERMIT2_APPROVE_SELECTOR)),
        )
        return CopyGrantPreview(verified, p.source, p.allocationBps, enable, p.followId)
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
        val owner = EvmAddress.parse(enable.account) // = the device owner validated in prepareGrant
        val built = api.buildEnableUserOp(BuildEnableRequest(preview.followId, enable.permissionId))
        val packed = with(built.userOp) {
            Erc4337UserOp.pack(
                sender = sender, nonce = nonce, callData = callData,
                callGasLimit = callGasLimit, verificationGasLimit = verificationGasLimit, preVerificationGas = preVerificationGas,
                maxFeePerGas = maxFeePerGas, maxPriorityFeePerGas = maxPriorityFeePerGas,
                factory = factory, factoryData = factoryData, paymaster = paymaster,
                paymasterVerificationGasLimit = paymasterVerificationGasLimit,
                paymasterPostOpGasLimit = paymasterPostOpGasLimit, paymasterData = paymasterData,
            )
        }
        // P0 — bind the owner's (full-authority root) signature to an op that enables EXACTLY our session.
        EnableUserOpVerifier.verify(
            userOp = packed, digestToSign = built.digestToSign, chainId = enable.chainId, expectedAccount = owner.value,
            sessionValidator = enable.sessionValidator, sessionValidatorInitData = enable.sessionValidatorInitData,
            salt = enable.salt, permissions = enable.permissions,
        )
        // P0 (KAN-160) — on the first enable, the op also carries a 7702 authorization (a SEPARATE full-authority
        // delegation). Pin its delegate-target + chain BEFORE signing; sign both digests in one seed window.
        val auth = built.authorizationToSign
        val authDigest = auth?.let { Eip7702Authorization.verify(it.chainId, it.address, it.nonce, enable.chainId) }
        val userOpDigest = Hex.decodeOrNull(built.digestToSign.removePrefix("0x"))
            ?: throw IllegalArgumentException("digestToSign not valid hex")
        val digests = if (authDigest != null) listOf(authDigest, userOpDigest) else listOf(userOpDigest)
        val signatures = aaSigner.signDigests(digests, owner, seedSource)
        val signedAuthorization = auth?.let { tuple -> signedAuthorization(tuple, signatures[0]) }
        val userOpHash = api.submitEnableUserOp(
            SubmitEnableRequest(built.userOpHash, signatures.last(), signedAuthorization),
        )

        repeat(maxPollAttempts) {
            val status = api.opStatus(enable.chainId, userOpHash)
            when (status.status) {
                "included" -> {
                    api.grantSession(CopyGrantRequest(enable.permissionId, preview.followId))
                    return CopyGrantResult(enable.permissionId, userOpHash, status.txHash)
                }
                "failed" -> throw IllegalStateException("enable userOp reverted on-chain: $userOpHash")
            }
            delay(pollDelayMs)
        }
        throw IllegalStateException("enable userOp not included after $maxPollAttempts polls: $userOpHash")
    }

    /** Splits a 65-byte `r‖s‖v` signature into the EIP-7702 [SignedAuthorization] (yParity = v − 27). */
    private fun signedAuthorization(tuple: AuthorizationTuple, signatureHex: String): SignedAuthorization {
        val sig = Hex.decodeOrNull(signatureHex.removePrefix("0x"))
            ?: throw IllegalStateException("authorization signature not valid hex")
        require(sig.size == 65) { "expected a 65-byte signature, got ${sig.size}" }
        val v = sig[64].toInt() and 0xFF
        return SignedAuthorization(
            chainId = tuple.chainId, address = tuple.address, nonce = tuple.nonce,
            r = "0x" + Hex.encode(sig.copyOfRange(0, 32)),
            s = "0x" + Hex.encode(sig.copyOfRange(32, 64)),
            yParity = v - 27,
        )
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
    val followId: String,
)

data class CopyGrantResult(val permissionId: String, val userOpHash: String, val txHash: String?)
