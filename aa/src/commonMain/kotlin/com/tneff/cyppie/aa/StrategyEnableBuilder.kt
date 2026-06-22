package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.SmartSessionEnableDigest
import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions

/** One per-token SELL-cap in a strategy basket: the SpendingLimit on [token]'s approve. */
data class StrategyCap(val token: String, val capBaseUnits: String)

/**
 * The on-device-built Smart-Strategy ENABLE (PRD-07b Vaults-B, KAN-165) — the **M-cap** sibling of
 * [CopyEnableBuilder]. The OwnableValidator owner is the **backend-held session public key** (24/7 rebalancing
 * within scope, like Copy); the user authorizes it once on-device (`SmartSessionGrantVerifier.verifyBasketGrant`
 * + owner-sign). Shape: `userOpPolicies=[TimeFrame]`; `actions = M×(token.approve→[SpendingLimits sell-cap])`
 * over the user's basket(+budget) tokens, then `Permit2.approve→[TimeFrame]` + `UniversalRouter.execute→[TimeFrame]`.
 *
 * The cap tokens are the on-chain SELL set (🔒); the swap `tokenOut` (buy direction) + the target weights are
 * advisory (ℹ️). Reuses [DcaEnableBuilder]'s audited ABI encoders + [CopyEnableBuilder]'s pins; the same
 * [EnableBroadcaster]/`verifyEnableUserOp`/`verify7702`/[RevokeBroadcaster] path runs on top, byte-identical to
 * Copy/DCA except for this session-config. (Action ordering reconciles against the backend KAN-164 vector.)
 */
data class BuiltStrategyEnable(
    val digestToSign: String,
    val account: String,             // the user's SCA (= owner EOA, 7702 same-address)
    val chainId: Long,
    val sessionValidator: String,
    val sessionValidatorInitData: String,
    val salt: String,
    val nonce: String,
    val permissionId: String,
    val permissions: SignedPermissions,
    val capTokens: List<String>,     // the basket+budget tokens (= verifyBasketGrant expectedCapTokens)
)

object StrategyEnableBuilder {

    val PERMIT2: String get() = CopyEnableBuilder.PERMIT2
    val PERMIT2_APPROVE_SELECTOR: String get() = CopyEnableBuilder.PERMIT2_APPROVE_SELECTOR
    val UNIVERSAL_ROUTER_EXECUTE_SELECTOR: String get() = CopyEnableBuilder.UNIVERSAL_ROUTER_EXECUTE_SELECTOR

    fun universalRouter(chainId: Long): String? = CopyEnableBuilder.universalRouter(chainId)

    /** Permissions for a strategy: M per-token sell-caps + Permit2 + UniversalRouter, all window-policed. */
    fun permissions(
        chainId: Long,
        caps: List<StrategyCap>,
        windowStart: Long,
        windowEnd: Long,
    ): SignedPermissions {
        require(caps.isNotEmpty()) { "a strategy needs at least one capped token" }
        val ur = universalRouter(chainId) ?: throw IllegalArgumentException("unsupported chainId $chainId")
        val window = PolicyData(DcaEnableBuilder.TIMEFRAME_POLICY, DcaEnableBuilder.timeFrameInitData(validAfter = windowStart, validUntil = windowEnd))
        val capActions = caps.map { cap ->
            ActionData(
                DcaEnableBuilder.APPROVE_SELECTOR,
                cap.token,
                listOf(PolicyData(DcaEnableBuilder.SPENDING_LIMIT_POLICY, DcaEnableBuilder.spendingLimitInitData(cap.token, cap.capBaseUnits))),
            )
        }
        return SignedPermissions(
            permitERC4337Paymaster = true,
            userOpPolicies = listOf(window),
            actions = capActions + listOf(
                ActionData(PERMIT2_APPROVE_SELECTOR, PERMIT2, listOf(window)),
                ActionData(UNIVERSAL_ROUTER_EXECUTE_SELECTOR, ur, listOf(window)),
            ),
        )
    }

    /**
     * The complete on-device strategy enable for the user's [account] SCA, owned by the backend [sessionPublicKey].
     * The enable digest is the pure EIP-712 [SmartSessionEnableDigest] over the disclosed material — the app then
     * `verifyBasketGrant`s it and owner-signs the digest (no blind signing). [salt] is app-chosen; [nonce] from RPC.
     */
    fun build(
        chainId: Long,
        account: String,
        sessionPublicKey: String,
        caps: List<StrategyCap>,
        windowStart: Long,
        windowEnd: Long,
        salt: String,
        nonce: Long,
    ): BuiltStrategyEnable {
        val initData = DcaEnableBuilder.ownableInitData(sessionPublicKey)
        val permissions = permissions(chainId, caps, windowStart, windowEnd)
        val digest = "0x" + Hex.encode(
            SmartSessionEnableDigest.enableDigest(
                account = account,
                chainId = chainId,
                sessionValidator = DcaEnableBuilder.OWNABLE_VALIDATOR,
                sessionValidatorInitData = initData,
                salt = salt,
                nonce = nonce.toString(),
                permissions = permissions,
            ),
        )
        return BuiltStrategyEnable(
            digestToSign = digest,
            account = account,
            chainId = chainId,
            sessionValidator = DcaEnableBuilder.OWNABLE_VALIDATOR,
            sessionValidatorInitData = initData,
            salt = salt,
            nonce = nonce.toString(),
            permissionId = DcaEnableBuilder.permissionId(DcaEnableBuilder.OWNABLE_VALIDATOR, initData, salt),
            permissions = permissions,
            capTokens = caps.map { it.token },
        )
    }
}
