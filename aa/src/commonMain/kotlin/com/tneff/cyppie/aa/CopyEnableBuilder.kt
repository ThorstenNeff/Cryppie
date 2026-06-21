package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.SmartSessionEnableDigest
import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import com.tneff.cyppie.evm.SmartSessionGrantVerifier

/**
 * The on-device-built Copy-trading Smart-Session ENABLE (PRD-06 Ph2, KAN-154). Like [DcaEnableBuilder] but the
 * OwnableValidator owner is the **backend-held session public key** (not the device owner) — the backend signs
 * copy ops 24/7 within the scoped, capped, on-chain-limited session; the user authorizes it **once** on-device
 * (`SmartSessionGrantVerifier.verifyGrant` + owner-sign). Reuses [DcaEnableBuilder]'s audited ABI encoders.
 *
 * Shape = the production **C6 three-action UniversalRouter** set (the SAME source of truth as the backend's
 * `assembleEnableInputs`): `userOpPolicies=[TimeFrame]`; `actions=[ token.approve→[SpendingLimits cap],
 * Permit2.approve→[TimeFrame], UniversalRouter.execute→[TimeFrame] ]`. The cap is **cumulative total budget**
 * (N3). Pins are single-sourced from [SmartSessionGrantVerifier].
 */
data class BuiltCopyEnable(
    val digestToSign: String,
    val account: String,             // the follower SCA (= owner EOA, 7702 same-address)
    val chainId: Long,
    val sessionValidator: String,
    val sessionValidatorInitData: String,
    val salt: String,
    val nonce: String,
    val permissionId: String,
    val permissions: SignedPermissions,
)

object CopyEnableBuilder {

    val PERMIT2: String get() = SmartSessionGrantVerifier.PERMIT2
    val PERMIT2_APPROVE_SELECTOR: String get() = SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR
    val UNIVERSAL_ROUTER_EXECUTE_SELECTOR: String get() = SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR

    /** The Uniswap UniversalRouter for [chainId] (client-pinned), or null if unsupported. */
    fun universalRouter(chainId: Long): String? = SmartSessionGrantVerifier.universalRouter(chainId)

    /** The production C6 three-action permissions for a copy session (cap on the token approve; window everywhere). */
    fun permissions(
        chainId: Long,
        spendToken: String,
        capBaseUnits: String,
        windowStart: Long,
        windowEnd: Long,
    ): SignedPermissions {
        val ur = universalRouter(chainId) ?: throw IllegalArgumentException("unsupported chainId $chainId")
        val window = PolicyData(DcaEnableBuilder.TIMEFRAME_POLICY, DcaEnableBuilder.timeFrameInitData(validAfter = windowStart, validUntil = windowEnd))
        return SignedPermissions(
            permitERC4337Paymaster = true,
            userOpPolicies = listOf(window),
            actions = listOf(
                ActionData(
                    DcaEnableBuilder.APPROVE_SELECTOR,
                    spendToken,
                    listOf(PolicyData(DcaEnableBuilder.SPENDING_LIMIT_POLICY, DcaEnableBuilder.spendingLimitInitData(spendToken, capBaseUnits))),
                ),
                ActionData(PERMIT2_APPROVE_SELECTOR, PERMIT2, listOf(window)),
                ActionData(UNIVERSAL_ROUTER_EXECUTE_SELECTOR, ur, listOf(window)),
            ),
        )
    }

    /**
     * The complete on-device Copy enable for the [follower] SCA, owned by the backend [sessionPublicKey]. The
     * enable digest is the pure EIP-712 [SmartSessionEnableDigest] over the disclosed material — the app then
     * `verifyGrant`s it and owner-signs the digest (no blind signing). [salt] is app-chosen; [nonce] is read
     * from the SmartSession module via RPC.
     */
    fun build(
        chainId: Long,
        follower: String,
        sessionPublicKey: String,
        spendToken: String,
        capBaseUnits: String,
        windowStart: Long,
        windowEnd: Long,
        salt: String,
        nonce: Long,
    ): BuiltCopyEnable {
        val initData = DcaEnableBuilder.ownableInitData(sessionPublicKey)
        val permissions = permissions(chainId, spendToken, capBaseUnits, windowStart, windowEnd)
        val digest = "0x" + Hex.encode(
            SmartSessionEnableDigest.enableDigest(
                account = follower,
                chainId = chainId,
                sessionValidator = DcaEnableBuilder.OWNABLE_VALIDATOR,
                sessionValidatorInitData = initData,
                salt = salt,
                nonce = nonce.toString(),
                permissions = permissions,
            ),
        )
        return BuiltCopyEnable(
            digestToSign = digest,
            account = follower,
            chainId = chainId,
            sessionValidator = DcaEnableBuilder.OWNABLE_VALIDATOR,
            sessionValidatorInitData = initData,
            salt = salt,
            nonce = nonce.toString(),
            permissionId = DcaEnableBuilder.permissionId(DcaEnableBuilder.OWNABLE_VALIDATOR, initData, salt),
            permissions = permissions,
        )
    }
}
