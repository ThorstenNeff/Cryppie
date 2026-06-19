package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-144 — `verifyGrant` (V1+decode): the decoders are pinned against the backend Vector-B `initData`, and
 * the full verify reproduces Vector-B (chainId 1) → a [VerifiedGrant] whose cap/token/window are decoded from
 * the signed bytes. Fail-closed on digest / pinned-policy / shape / broad-flag mismatch.
 */
class SmartSessionGrantVerifierTest {

    // Vector-B literals (Backend enable-vector.mjs).
    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val sessionValidator = "0x0000000000000000000000000000000000000777"
    private val sessionValidatorInitData = "0x000000000000000000000000cafecafecafecafecafecafecafecafecafecafe"
    private val salt = "0x0000000000000000000000000000000000000000000000000000000000000001"
    private val nonce = "0"
    private val spendingLimitPolicy = "0x0000000000000000000000000000000000000511"
    private val timeFramePolicy = "0x0000000000000000000000000000000000000522"
    private val router = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"
    private val selector = "0x5ae401dc"
    private val spendInitData = "0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" +
        "00000000000000000000000000000000000000000000000000000000000f4240"
    private val timeInitData = "0x00000000000000000000000000000000000000000000000000000000683f9e80" +
        "00000000000000000000000000000000000000000000000000000000687a4f00"
    private val digestB1 = "0x4b7fe8e3ab2cf1929e70dda85aee35b48a005ce95a0d73a94f2221351ce62632"

    private fun dcaPermissions(
        spendPolicy: String = spendingLimitPolicy,
        timePolicy: String = timeFramePolicy,
        admin: Boolean = false,
    ) = SignedPermissions(
        permitAdminAccess = admin,
        userOpPolicies = listOf(PolicyData(spendPolicy, spendInitData)),
        actions = listOf(ActionData(selector, router, listOf(PolicyData(timePolicy, timeInitData)))),
    )

    private fun verify(permissions: SignedPermissions = dcaPermissions(), digest: String = digestB1) =
        SmartSessionGrantVerifier.verifyGrant(
            account = account, chainId = 1L, sessionValidator = sessionValidator,
            sessionValidatorInitData = sessionValidatorInitData, salt = salt, nonce = nonce,
            permissions = permissions, digestToSign = digest,
            spendingLimitPolicy = spendingLimitPolicy, timeFramePolicy = timeFramePolicy,
            sessionValidatorPin = sessionValidator, // Vector-B uses the placeholder validator
        )

    // ── decoders pinned vs Vector-B ──

    @Test
    fun decodesSpendingLimitTokenAndCap() {
        val (token, cap) = SmartSessionGrantVerifier.decodeSpendingLimit(spendInitData)
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", token) // USDC, EIP-55
        assertEquals("1000000", cap) // 0x0f4240, base units
    }

    @Test
    fun decodesTimeFrameWindow() {
        val (start, end) = SmartSessionGrantVerifier.decodeTimeFrame(timeInitData)
        assertEquals(0x683f9e80L, start)
        assertEquals(0x687a4f00L, end)
    }

    @Test
    fun rejectsWrongInitDataShape() {
        assertFailsWith<GrantVerificationException> { SmartSessionGrantVerifier.decodeSpendingLimit("0x1234") }
    }

    // ── full verifyGrant ──

    @Test
    fun verifiesVectorBAndReturnsDecodedDisplay() {
        val grant = verify()
        assertEquals(1L, grant.chainId)
        assertEquals(account, grant.account)
        assertEquals(router, grant.actionTarget)
        assertEquals(selector, grant.actionSelector)
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", grant.spendToken)
        assertEquals("1000000", grant.capBaseUnits)
        assertEquals(0x683f9e80L, grant.windowStartEpochSeconds)
        assertEquals(0x687a4f00L, grant.windowEndEpochSeconds)
    }

    @Test
    fun failsClosedOnDigestMismatch() {
        assertFailsWith<GrantVerificationException> {
            verify(digest = "0x" + "00".repeat(32)) // not the real enable digest
        }
    }

    @Test
    fun failsClosedOnSwappedSpendingPolicy() {
        // A malicious backend points the spending limit at a different (un-pinned) policy contract.
        assertFailsWith<GrantVerificationException> {
            verify(permissions = dcaPermissions(spendPolicy = "0x00000000000000000000000000000000000009ff"))
        }
    }

    @Test
    fun failsClosedOnBroadAccessFlag() {
        // permitAdminAccess=true changes the digest too, but the explicit flag check is the readable gate.
        val perms = dcaPermissions(admin = true)
        val digest = "0x" + Hex.encode(
            SmartSessionEnableDigest.enableDigest(
                account, 1L, sessionValidator, sessionValidatorInitData, salt, nonce, perms,
            ),
        )
        assertFailsWith<GrantVerificationException> { verify(permissions = perms, digest = digest) }
    }

    // Vector-D production path: real OwnableValidator + real policies + paymaster=true, ALL default pins.
    private val validatorD = "0x000000000013fdB5234E4E3162a810F54d9f7E98"
    private val validatorInitDataD = "0x" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "0000000000000000000000000000000000000000000000000000000000000040" +
        "0000000000000000000000000000000000000000000000000000000000000001" +
        "000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb92266"
    private val permsD = SignedPermissions(
        permitERC4337Paymaster = true,
        userOpPolicies = listOf(PolicyData(SmartSessionGrantVerifier.SPENDING_LIMIT_POLICY, spendInitData)),
        actions = listOf(ActionData(selector, router, listOf(PolicyData(SmartSessionGrantVerifier.TIMEFRAME_POLICY, timeInitData)))),
    )

    @Test
    fun verifiesVectorD_allDefaultPins() {
        // The REAL DCA grant (validator 0x…7E98 + GLOBAL_CONSTANTS policies + paymaster) verifies with NO
        // overrides → exercises every production default pin (validator + both policies).
        val digest = "0x" + Hex.encode(
            SmartSessionEnableDigest.enableDigest(account, 1L, validatorD, validatorInitDataD, salt, nonce, permsD),
        )
        val grant = SmartSessionGrantVerifier.verifyGrant(
            account = account, chainId = 1L, sessionValidator = validatorD,
            sessionValidatorInitData = validatorInitDataD, salt = salt, nonce = nonce,
            permissions = permsD, digestToSign = digest, // no overrides → all production default pins
        )
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", grant.spendToken)
        assertEquals("1000000", grant.capBaseUnits)
    }

    @Test
    fun failsClosedOnNonPinnedValidator() {
        // A session built with a validator other than the pinned OwnableValidator (e.g. the legacy export) is
        // rejected by the default sessionValidatorPin — even though its self-computed digest matches.
        val perms = dcaPermissions()
        val digest = "0x" + Hex.encode(
            SmartSessionEnableDigest.enableDigest(account, 1L, sessionValidator, sessionValidatorInitData, salt, nonce, perms),
        )
        assertFailsWith<GrantVerificationException> {
            SmartSessionGrantVerifier.verifyGrant(
                account = account, chainId = 1L, sessionValidator = sessionValidator, // placeholder 0x…0777
                sessionValidatorInitData = sessionValidatorInitData, salt = salt, nonce = nonce,
                permissions = perms, digestToSign = digest,
                spendingLimitPolicy = spendingLimitPolicy, timeFramePolicy = timeFramePolicy,
                // no sessionValidatorPin override → default 0x…7E98 ≠ 0x…0777 → reject
            )
        }
    }

    @Test
    fun productionPinsAreGlobalConstantsNotLegacy() {
        // Guard the emitted GLOBAL_CONSTANTS addresses against accidental drift / a legacy-constants.js swap.
        assertEquals("0x000000000033212e272655d8a22402db819477a6", SmartSessionGrantVerifier.SPENDING_LIMIT_POLICY)
        assertEquals("0x0000000000D30f611fA3bf652ac6879428586930", SmartSessionGrantVerifier.TIMEFRAME_POLICY)
        assertEquals("0x000000000013fdB5234E4E3162a810F54d9f7E98", SmartSessionGrantVerifier.SESSION_VALIDATOR)
    }
}
