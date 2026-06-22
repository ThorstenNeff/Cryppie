package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.EvmAddress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A scriptable StrategyApi for the prepare-disclosure phase (build/submit/opStatus unused here → stubbed). */
private class FakeStrategyApi(private val prepared: StrategyPrepare) : StrategyApi {
    override suspend fun prepare(request: StrategyScopeRequest) = prepared
    override suspend fun grantSession(request: StrategyGrantRequest) {}
    override suspend fun buildEnableUserOp(request: BuildEnableRequest) = throw NotImplementedError()
    override suspend fun submitEnableUserOp(request: SubmitEnableRequest) = throw NotImplementedError()
    override suspend fun opStatus(chainId: Long, userOpHash: String) = throw NotImplementedError()
}

/**
 * KAN-165 — structure test of the Strategy orchestration disclosure (modeled on FollowGrantService): prepare →
 * StrategyEnableBuilder → verifyBasketGrant → preview (🔒 sell-caps verified, ℹ️ weights advisory). The full
 * authorize/broadcast E2E byte-pins against the backend KAN-164 strategy-enable vector (follow-up).
 */
class StrategyGrantServiceTest {

    private val owner = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val sessionPubkey = "0x489ccacAC8836C71Ad5B20Bf61e0b885425b227e"
    private val salt = "0x00000000000000000000000000000000000000000000000000000000000000aa"
    private val weth = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
    private val usdc = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"

    private val caps = listOf(StrategyCap(weth, "1000000000000000000"), StrategyCap(usdc, "1000000000"))
    private val weights = listOf(StrategyWeight(weth, 6000), StrategyWeight(usdc, 4000))
    private val permissionId = DcaEnableBuilder.permissionId(DcaEnableBuilder.OWNABLE_VALIDATOR, DcaEnableBuilder.ownableInitData(sessionPubkey), salt)

    private val prepared = StrategyPrepare(
        permissionId = permissionId, chainId = 1L, follower = owner.value, sessionPublicKey = sessionPubkey,
        caps = caps, windowStart = 0L, windowEnd = 1893456000L, salt = salt, nonce = 0L, weights = weights,
    )

    private val scope = StrategyScopeRequest(
        chainId = 1L, follower = owner.value, budgetToken = usdc, budget = "1000000000", basket = weights,
        windowStart = 0L, windowEnd = 1893456000L,
    )

    @Test
    fun prepareGrant_disclosesVerifiedSellCaps_andAdvisoryWeights() = runTest {
        val svc = StrategyGrantService(FakeStrategyApi(prepared))
        val preview = svc.prepareGrant(scope, owner)
        // 🔒 crypto-verified SELL-cap scope:
        assertEquals(2, preview.verifiedGrant.caps.size)
        assertEquals("0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af", preview.verifiedGrant.actionTarget) // UR ETH
        assertEquals(1893456000L, preview.verifiedGrant.windowEndEpochSeconds)
        // ℹ️ advisory weights (rendered separately, not a guarantee):
        assertEquals(weights, preview.weights)
        assertEquals(permissionId, preview.enable.permissionId)
    }

    @Test
    fun prepareGrant_refusesBackendFollowerMismatch() = runTest {
        val rogue = prepared.copy(follower = "0x000000000000000000000000000000000000dEaD")
        val svc = StrategyGrantService(FakeStrategyApi(rogue))
        assertFailsWith<IllegalArgumentException> { svc.prepareGrant(scope, owner) }
    }

    @Test
    fun prepareGrant_refusesPermissionIdMismatch() = runTest {
        val rogue = prepared.copy(permissionId = "0x0000000000000000000000000000000000000000000000000000000000000bad")
        val svc = StrategyGrantService(FakeStrategyApi(rogue))
        assertFailsWith<IllegalArgumentException> { svc.prepareGrant(scope, owner) }
    }
}
