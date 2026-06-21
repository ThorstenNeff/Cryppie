package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-150 — `verifyGrant` over the **C3-corrected** DCA shape (TimeFrame in userOpPolicies; cap on the
 * token-approve action; swap separate) with the **real audited initData** (`abi.encode(address[],uint256[])`
 * spending-limit; `encodePacked(uint48 validUntil, uint48 validAfter)` time-frame). The full verify reproduces
 * the real **Vector D** (chainId 1) → a [VerifiedGrant] whose token/cap/window are decoded from the signed bytes.
 */
class SmartSessionGrantVerifierTest {

    // Vector-D literals (Backend enable-vector.mjs, C3-corrected).
    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val validator = "0x000000000013fdB5234E4E3162a810F54d9f7E98"
    private val validatorInitData = "0x" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "0000000000000000000000000000000000000000000000000000000000000040" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb92266"
    private val salt = "0x0000000000000000000000000000000000000000000000000000000000000001"
    private val nonce = "0"
    private val usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private val router = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"
    private val approveSelector = "0x095ea7b3"
    private val swapSelector = "0x5ae401dc"
    private val spendInitData = "0x" +
        "0000000000000000000000000000000000000000000000000000000000000040" +
        "0000000000000000000000000000000000000000000000000000000000000080" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "00000000000000000000000000000000000000000000000000000000000f4240"
    private val timeInitData = "0x0000687c05000000683f1a80"
    private val digestD1 = "0x8be4818ec1fb3068b671a68158eef275f922735658a18908069fe29225898c5c"

    private val spendPolicy = SmartSessionGrantVerifier.SPENDING_LIMIT_POLICY
    private val timePolicy = SmartSessionGrantVerifier.TIMEFRAME_POLICY

    private fun dcaPermissions(
        approveSel: String = approveSelector,
        capPolicy: String = spendPolicy,
        admin: Boolean = false,
        oneAction: Boolean = false,
    ): SignedPermissions {
        val approve = ActionData(approveSel, usdc, listOf(PolicyData(capPolicy, spendInitData)))
        val swap = ActionData(swapSelector, router, listOf(PolicyData(timePolicy, timeInitData)))
        return SignedPermissions(
            permitERC4337Paymaster = true,
            permitAdminAccess = admin,
            userOpPolicies = listOf(PolicyData(timePolicy, timeInitData)),
            actions = if (oneAction) listOf(approve) else listOf(approve, swap),
        )
    }

    private fun digestOf(permissions: SignedPermissions): String = "0x" + Hex.encode(
        SmartSessionEnableDigest.enableDigest(account, 1L, validator, validatorInitData, salt, nonce, permissions),
    )

    private fun verify(permissions: SignedPermissions, digest: String) =
        SmartSessionGrantVerifier.verifyGrant(
            account = account, chainId = 1L, sessionValidator = validator,
            sessionValidatorInitData = validatorInitData, salt = salt, nonce = nonce,
            permissions = permissions, digestToSign = digest,
            swapTarget = router, swapSelector = swapSelector, // DCA: single swap action, no infra
        )

    // ── decoders (real audited layouts) ──

    @Test
    fun decodesSpendingLimitArrayEncoding() {
        val (token, cap) = SmartSessionGrantVerifier.decodeSpendingLimit(spendInitData)
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", token) // USDC, EIP-55
        assertEquals("1000000", cap)
    }

    @Test
    fun decodesPackedUint48TimeFrame() {
        val (start, end) = SmartSessionGrantVerifier.decodeTimeFrame(timeInitData)
        assertEquals(1748966016L, start) // validAfter (0x683f1a80)
        assertEquals(1752958208L, end)   // validUntil (0x687c0500)
    }

    @Test
    fun rejectsWrongSpendingLimitLength() {
        assertFailsWith<GrantVerificationException> { SmartSessionGrantVerifier.decodeSpendingLimit("0x1234") }
    }

    @Test
    fun rejectsWrongTimeFrameLength() {
        assertFailsWith<GrantVerificationException> { SmartSessionGrantVerifier.decodeTimeFrame(spendInitData) }
    }

    // ── full verifyGrant (real Vector D, all default pins) ──

    @Test
    fun verifiesVectorD_andReturnsSwapTargetWithApproveCap() {
        val grant = verify(dcaPermissions(), digestD1)
        assertEquals(1L, grant.chainId)
        assertEquals(account, grant.account)
        assertEquals(router, grant.actionTarget)        // the swap action (router) is the user-facing target
        assertEquals(swapSelector, grant.actionSelector)
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", grant.spendToken) // from the approve cap
        assertEquals("1000000", grant.capBaseUnits)
        assertEquals(1748966016L, grant.windowStartEpochSeconds)
        assertEquals(1752958208L, grant.windowEndEpochSeconds)
    }

    @Test
    fun failsClosedOnDigestMismatch() {
        assertFailsWith<GrantVerificationException> { verify(dcaPermissions(), "0x" + "00".repeat(32)) }
    }

    @Test
    fun failsClosedOnSwappedSpendingPolicy() {
        val perms = dcaPermissions(capPolicy = "0x00000000000000000000000000000000000009ff")
        assertFailsWith<GrantVerificationException> { verify(perms, digestOf(perms)) }
    }

    @Test
    fun failsClosedOnBroadAccessFlag() {
        val perms = dcaPermissions(admin = true)
        assertFailsWith<GrantVerificationException> { verify(perms, digestOf(perms)) }
    }

    @Test
    fun failsClosedOnWrongActionCount() {
        val perms = dcaPermissions(oneAction = true) // only the approve action, no swap
        assertFailsWith<GrantVerificationException> { verify(perms, digestOf(perms)) }
    }

    @Test
    fun failsClosedOnMissingApproveSelector() {
        val perms = dcaPermissions(approveSel = "0xdeadbeef") // no approve action → can't locate the cap
        assertFailsWith<GrantVerificationException> { verify(perms, digestOf(perms)) }
    }

    @Test
    fun failsClosedOnNonPinnedValidator() {
        val perms = dcaPermissions()
        val placeholder = "0x0000000000000000000000000000000000000777"
        val digest = "0x" + Hex.encode(
            SmartSessionEnableDigest.enableDigest(account, 1L, placeholder, validatorInitData, salt, nonce, perms),
        )
        assertFailsWith<GrantVerificationException> {
            SmartSessionGrantVerifier.verifyGrant(
                account = account, chainId = 1L, sessionValidator = placeholder,
                sessionValidatorInitData = validatorInitData, salt = salt, nonce = nonce,
                permissions = perms, digestToSign = digest, // default validator pin 0x…7E98 ≠ placeholder
                swapTarget = router, swapSelector = swapSelector,
            )
        }
    }

    @Test
    fun pinsAreGlobalConstantsNotLegacy() {
        assertEquals("0x000000000033212e272655d8a22402db819477a6", SmartSessionGrantVerifier.SPENDING_LIMIT_POLICY)
        assertEquals("0x0000000000D30f611fA3bf652ac6879428586930", SmartSessionGrantVerifier.TIMEFRAME_POLICY)
        assertEquals("0x000000000013fdB5234E4E3162a810F54d9f7E98", SmartSessionGrantVerifier.SESSION_VALIDATOR)
        assertEquals("0x095ea7b3", SmartSessionGrantVerifier.APPROVE_SELECTOR)
        assertEquals("0x000000000022D473030F116dDEE9F6B43aC78BA3", SmartSessionGrantVerifier.PERMIT2)
        assertEquals("0x87517c45", SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR)
        assertEquals("0x3593564c", SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR)
        assertEquals("0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af", SmartSessionGrantVerifier.universalRouter(1L))
        assertEquals("0x6fF5693b99212Da76ad316178A184AB56D299b43", SmartSessionGrantVerifier.universalRouter(8453L))
    }

    // ── Copy-trading (KAN-154): the production 3-action UniversalRouter shape, against the backend Copy KAT ──
    // Canonical inputs = aa-trigger/scripts/copy-vector.mjs (sessionPubkey 0x489c…227e, salt 0x…aa, follower 0xf39F…,
    // window 0..1893456000, cap 1000 USDC). Reproduces the backend ETH enable digest 0xaa03c762….

    private val copyAccount = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" // follower SCA
    private val copySessionValidatorInitData = "0x" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "0000000000000000000000000000000000000000000000000000000000000040" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "000000000000000000000000489ccacac8836c71ad5b20bf61e0b885425b227e" // owners[0] = backend session pubkey
    private val copySalt = "0x00000000000000000000000000000000000000000000000000000000000000aa"
    private val copySpendInitDataEth = "0x" +
        "0000000000000000000000000000000000000000000000000000000000000040" +
        "0000000000000000000000000000000000000000000000000000000000000080" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "000000000000000000000000000000000000000000000000000000003b9aca00" // cap = 1_000_000_000 (1000 USDC)
    private val copyTimeInitData = "0x000070dbd880000000000000" // validUntil 1893456000, validAfter 0
    private val urEth = SmartSessionGrantVerifier.universalRouter(1L)!!
    private val permit2 = SmartSessionGrantVerifier.PERMIT2

    private fun copyPermissionsEth() = SignedPermissions(
        permitERC4337Paymaster = true,
        userOpPolicies = listOf(PolicyData(timePolicy, copyTimeInitData)),
        actions = listOf(
            ActionData(approveSelector, usdc, listOf(PolicyData(spendPolicy, copySpendInitDataEth))),
            ActionData(SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR, permit2, listOf(PolicyData(timePolicy, copyTimeInitData))),
            ActionData(SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR, urEth, listOf(PolicyData(timePolicy, copyTimeInitData))),
        ),
    )

    private fun verifyCopy(permissions: SignedPermissions, digest: String) =
        SmartSessionGrantVerifier.verifyGrant(
            account = copyAccount, chainId = 1L, sessionValidator = validator,
            sessionValidatorInitData = copySessionValidatorInitData, salt = copySalt, nonce = nonce,
            permissions = permissions, digestToSign = digest,
            swapTarget = urEth, swapSelector = SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR,
            infraActions = listOf(SmartSessionGrantVerifier.ActionPin(permit2, SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR)),
        )

    @Test
    fun verifiesCopyVectorEth_threeActionUniversalRouter() {
        val perms = copyPermissionsEth()
        val grant = verifyCopy(perms, "0xaa03c7623f8682f29eda13fd8fded094c796f499a34881c66d53e93ba2ae9a7c")
        assertEquals(urEth, grant.actionTarget) // the UniversalRouter is the user-facing swap target
        assertEquals("0x3593564c", grant.actionSelector)
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", grant.spendToken)
        assertEquals("1000000000", grant.capBaseUnits) // 1000 USDC total budget
        assertEquals(0L, grant.windowStartEpochSeconds)
        assertEquals(1893456000L, grant.windowEndEpochSeconds)
    }

    @Test
    fun copyFailsClosedOnRogueExtraAction() {
        // A rogue backend appends a 4th action on a non-pinned router → unknown action → fail-closed.
        val rogue = ActionData("0x3593564c", "0x000000000000000000000000000000000000bEEF", listOf(PolicyData(timePolicy, copyTimeInitData)))
        val perms = copyPermissionsEth().let { it.copy(actions = it.actions + rogue) }
        assertFailsWith<GrantVerificationException> { verifyCopy(perms, digestOfCopy(perms)) }
    }

    @Test
    fun copyFailsClosedOnMissingInfraAction() {
        // Drop the Permit2 leg → the pinned infra action is missing → fail-closed.
        val perms = copyPermissionsEth().let { it.copy(actions = it.actions.filterNot { a -> a.actionTarget.equals(permit2, true) }) }
        assertFailsWith<GrantVerificationException> { verifyCopy(perms, digestOfCopy(perms)) }
    }

    private fun digestOfCopy(permissions: SignedPermissions): String = "0x" + Hex.encode(
        SmartSessionEnableDigest.enableDigest(copyAccount, 1L, validator, copySessionValidatorInitData, copySalt, nonce, permissions),
    )

    // ── P2 hardening: every fail-closed path explicitly pinned ──

    @Test
    fun copyFailsClosedOnDuplicateInfraAction() {
        // Two Permit2 legs → the same infra pin matched twice → duplicate → fail-closed.
        val dupPermit2 = ActionData(SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR, permit2, listOf(PolicyData(timePolicy, copyTimeInitData)))
        val perms = copyPermissionsEth().let { it.copy(actions = it.actions + dupPermit2) }
        assertFailsWith<GrantVerificationException> { verifyCopy(perms, digestOfCopy(perms)) }
    }

    @Test
    fun copyFailsClosedOnExtraPolicyOnAction() {
        // The swap action carries TWO policies → singleOrNull is null → fail-closed.
        val perms = copyPermissionsEth()
        val tampered = perms.copy(
            actions = perms.actions.map { a ->
                if (a.actionTarget.equals(urEth, true)) {
                    a.copy(actionPolicies = a.actionPolicies + PolicyData(timePolicy, copyTimeInitData))
                } else {
                    a
                }
            },
        )
        assertFailsWith<GrantVerificationException> { verifyCopy(tampered, digestOfCopy(tampered)) }
    }

    @Test
    fun copyFailsClosedOnInfraWindowMismatch() {
        // The Permit2 leg's time-frame window differs from the userOp window → fail-closed.
        val otherWindow = "0x000070dbd880000000000001" // validAfter = 1 (≠ userOp window's 0)
        val perms = copyPermissionsEth()
        val tampered = perms.copy(
            actions = perms.actions.map { a ->
                if (a.actionTarget.equals(permit2, true)) a.copy(actionPolicies = listOf(PolicyData(timePolicy, otherWindow))) else a
            },
        )
        assertFailsWith<GrantVerificationException> { verifyCopy(tampered, digestOfCopy(tampered)) }
    }

    @Test
    fun verifiesCopyVectorBase_fullRoundTrip() {
        val usdcBase = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
        val urBase = SmartSessionGrantVerifier.universalRouter(8453L)!!
        val spendBase = "0x" +
            "0000000000000000000000000000000000000000000000000000000000000040" +
            "0000000000000000000000000000000000000000000000000000000000000080" +
            "0000000000000000000000000000000000000000000000000000000000000001" +
            "000000000000000000000000833589fcd6edb6e08f4c7c32d4f71b54bda02913" +
            "0000000000000000000000000000000000000000000000000000000000000001" +
            "000000000000000000000000000000000000000000000000000000003b9aca00"
        val perms = SignedPermissions(
            permitERC4337Paymaster = true,
            userOpPolicies = listOf(PolicyData(timePolicy, copyTimeInitData)),
            actions = listOf(
                ActionData(approveSelector, usdcBase, listOf(PolicyData(spendPolicy, spendBase))),
                ActionData(SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR, permit2, listOf(PolicyData(timePolicy, copyTimeInitData))),
                ActionData(SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR, urBase, listOf(PolicyData(timePolicy, copyTimeInitData))),
            ),
        )
        val grant = SmartSessionGrantVerifier.verifyGrant(
            account = copyAccount, chainId = 8453L, sessionValidator = validator,
            sessionValidatorInitData = copySessionValidatorInitData, salt = copySalt, nonce = nonce,
            permissions = perms, digestToSign = "0xe4c4661d938e57710806fe4de29aee40a3a2f6b50bb1adf08d635cf71519954a",
            swapTarget = urBase, swapSelector = SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR,
            infraActions = listOf(SmartSessionGrantVerifier.ActionPin(permit2, SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR)),
        )
        assertEquals(urBase, grant.actionTarget)
        assertEquals("1000000000", grant.capBaseUnits)
        assertEquals(1893456000L, grant.windowEndEpochSeconds)
    }
}
