package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Byte-exact pin of the on-device-built DCA ENABLE against the backend's **real** reference **vector D**
 * (`Backend/aa-trigger/scripts/enable-vector.mjs`, C3-corrected placement — KAN-150): emitted
 * OwnableValidator + GLOBAL_CONSTANTS policies + paymaster=true; the spend cap sits on the USDC `approve`
 * action, the TimeFrame window is a userOpPolicy + on the swap action, the DEX swap is a separate action.
 * A mismatch means the app would sign a digest the on-chain session can't validate.
 */
class DcaEnableBuilderTest {

    // Vector D literals (= enable-vector.mjs).
    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" // owner = on-device EOA = account (7702)
    private val salt = "0x0000000000000000000000000000000000000000000000000000000000000001"
    private val nonce = 0L
    private val usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private val capBaseUnits = "1000000" // 0x0f4240
    private val validAfter = 1748966016L // 0x683f1a80 — window start
    private val validUntil = 1752958208L // 0x687c0500 — window end
    private val router = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"
    private val swapSelector = "0x5ae401dc"

    @Test
    fun ownableInitData_matchesVectorD() {
        assertEquals(
            "0x" +
                "0000000000000000000000000000000000000000000000000000000000000001" + // threshold = 1
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset
                "0000000000000000000000000000000000000000000000000000000000000001" + // owners.length
                "000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb92266",   // owners[0]
            DcaEnableBuilder.ownableInitData(account),
        )
    }

    @Test
    fun spendingLimitInitData_matchesVectorD() {
        // abi.encode(address[] [USDC], uint256[] [1_000_000]) — the real SpendingLimitsPolicy shape (KAN-150).
        assertEquals(
            "0x" +
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset tokens[]
                "0000000000000000000000000000000000000000000000000000000000000080" + // offset limits[]
                "0000000000000000000000000000000000000000000000000000000000000001" + // tokens.length
                "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" + // tokens[0] = USDC
                "0000000000000000000000000000000000000000000000000000000000000001" + // limits.length
                "00000000000000000000000000000000000000000000000000000000000f4240",   // limits[0] = cap
            DcaEnableBuilder.spendingLimitInitData(usdc, capBaseUnits),
        )
    }

    @Test
    fun timeFrameInitData_matchesVectorD() {
        // abi.encodePacked(uint48 validUntil, uint48 validAfter) — 12 bytes (KAN-150).
        assertEquals("0x0000687c05000000683f1a80", DcaEnableBuilder.timeFrameInitData(validAfter, validUntil))
    }

    @Test
    fun permissionId_matchesVectorD() {
        assertEquals(
            "0x82bc397553fc6577974c762cd42958d860cd838a55f55f245ee5f6debab698b0",
            DcaEnableBuilder.permissionId(DcaEnableBuilder.OWNABLE_VALIDATOR, DcaEnableBuilder.ownableInitData(account), salt),
        )
    }

    @Test
    fun pinnedConstants_areTheEmittedGlobalConstants() {
        assertEquals("0x000000000013fdB5234E4E3162a810F54d9f7E98", DcaEnableBuilder.OWNABLE_VALIDATOR)
        assertEquals("0x000000000033212e272655d8a22402db819477a6", DcaEnableBuilder.SPENDING_LIMIT_POLICY)
        assertEquals("0x0000000000D30f611fA3bf652ac6879428586930", DcaEnableBuilder.TIMEFRAME_POLICY)
        assertEquals("0x095ea7b3", DcaEnableBuilder.APPROVE_SELECTOR)
    }

    @Test
    fun decodeNonce_acceptsRealZero_butRejectsEmptyOrShort() {
        assertEquals(0L, DcaEnableBuilder.decodeNonce(ByteArray(32)))          // real first-session nonce 0
        assertEquals(5L, DcaEnableBuilder.decodeNonce(ByteArray(32).also { it[31] = 5 }))
        assertFailsWith<IllegalStateException> { DcaEnableBuilder.decodeNonce(ByteArray(0)) }   // empty 0x → fail-closed
        assertFailsWith<IllegalStateException> { DcaEnableBuilder.decodeNonce(ByteArray(31)) }  // short → fail-closed
    }

    private fun built(chainId: Long): BuiltEnable {
        val config = SessionConfig(
            chainId = chainId,
            account = account,
            actions = listOf(
                ScopedAction(
                    target = router,
                    selector = swapSelector,
                    spendingLimits = listOf(SpendingLimit(usdc, capBaseUnits)),
                    rollingWindowSeconds = 86_400L,
                    usageLimit = 30,
                    validUntil = validUntil,
                ),
            ),
        )
        return DcaEnableBuilder.build(config, owner = account, salt = salt, nonce = nonce, windowStart = validAfter)
    }

    @Test
    fun enableDigest_matchesVectorD_chain1() {
        assertEquals("0x8be4818ec1fb3068b671a68158eef275f922735658a18908069fe29225898c5c", built(1L).digestToSign)
    }

    @Test
    fun enableDigest_matchesVectorD_base() {
        assertEquals("0xdacaa17a34fbd97bb7764de7e9f4a79517f22f239d513f3d28753c7762df99f8", built(8453L).digestToSign)
    }

    @Test
    fun built_carriesPermissionIdAndOwnerBoundPermissions() {
        val b = built(1L)
        assertEquals("0x82bc397553fc6577974c762cd42958d860cd838a55f55f245ee5f6debab698b0", b.permissionId)
        assertEquals(true, b.permissions.permitERC4337Paymaster)
        assertEquals(2, b.permissions.actions.size) // approve + swap (KAN-150 two-action shape)
    }

    /** The grant flow's self-check: the on-device-built enable must round-trip through verifyGrant (the new
     *  two-action decode, KAN-150) → the disclosure renders the swap router + the cap/token/window. */
    @Test
    fun verifyGrant_roundTrips_theBuiltEnable() {
        val b = built(1L)
        val v = SmartSessionGrantVerifier.verifyGrant(
            account = account,
            chainId = 1L,
            sessionValidator = b.sessionValidator,
            sessionValidatorInitData = b.sessionValidatorInitData,
            salt = b.salt,
            nonce = b.nonce,
            permissions = b.permissions,
            digestToSign = b.digestToSign,
            swapTarget = router, swapSelector = swapSelector, // DCA: single swap action, no infra (KAN-154)
        )
        assertTrue(v.actionTarget.equals(router, ignoreCase = true))    // = the swap router (Dev-2 confirmed)
        assertTrue(v.actionSelector.equals(swapSelector, ignoreCase = true))
        assertTrue(v.spendToken.equals(usdc, ignoreCase = true))        // cap token from the approve action
        assertEquals("1000000", v.capBaseUnits)
        assertEquals(validAfter, v.windowStartEpochSeconds)
        assertEquals(validUntil, v.windowEndEpochSeconds)
    }
}
