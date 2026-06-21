package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Byte-exact pin of the on-device-built Copy enable against the backend's shared KAT
 * (`Backend/aa-trigger/scripts/copy-vector.mjs` @909c12a): the production 3-action UniversalRouter shape with
 * the backend session key as the OwnableValidator owner. Plus a build → `verifyGrant` round-trip (the
 * app-side convergence self-check, mirroring the DCA Vector-D pattern).
 */
class CopyEnableBuilderTest {

    private val follower = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"      // follower SCA
    private val sessionPubkey = "0x489ccacAC8836C71Ad5B20Bf61e0b885425b227e" // backend session key (owners[0])
    private val salt = "0x00000000000000000000000000000000000000000000000000000000000000aa"
    private val cap = "1000000000" // 1000 USDC total budget (N3)
    private val windowEnd = 1893456000L
    private val usdcEth = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    private val usdcBase = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
    private val permissionId = "0x1c3f76fac3f146c12a665114ff61d6d257653434d854ecb3570c6b2c32e96b55"

    private fun built(chainId: Long, usdc: String) =
        CopyEnableBuilder.build(chainId, follower, sessionPubkey, usdc, cap, windowStart = 0, windowEnd = windowEnd, salt = salt, nonce = 0)

    @Test
    fun copyEnableMatchesVector_chain1() {
        val b = built(1L, usdcEth)
        assertEquals("0xaa03c7623f8682f29eda13fd8fded094c796f499a34881c66d53e93ba2ae9a7c", b.digestToSign)
        assertEquals(permissionId, b.permissionId) // chain-agnostic (validator + initData + salt)
    }

    @Test
    fun copyEnableMatchesVector_base() {
        val b = built(8453L, usdcBase)
        assertEquals("0xe4c4661d938e57710806fe4de29aee40a3a2f6b50bb1adf08d635cf71519954a", b.digestToSign)
        assertEquals(permissionId, b.permissionId)
    }

    @Test
    fun buildVerifyGrantRoundTrip_returnsUniversalRouterSwap() {
        // The app builds the enable, then verifyGrant decodes the disclosure from the SIGNED bytes (self-check).
        val b = built(1L, usdcEth)
        val grant = SmartSessionGrantVerifier.verifyGrant(
            account = b.account, chainId = b.chainId, sessionValidator = b.sessionValidator,
            sessionValidatorInitData = b.sessionValidatorInitData, salt = b.salt, nonce = b.nonce,
            permissions = b.permissions, digestToSign = b.digestToSign,
            swapTarget = SmartSessionGrantVerifier.universalRouter(1L)!!,
            swapSelector = SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR,
            infraActions = listOf(SmartSessionGrantVerifier.ActionPin(SmartSessionGrantVerifier.PERMIT2, SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR)),
        )
        assertEquals(SmartSessionGrantVerifier.universalRouter(1L), grant.actionTarget)
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", grant.spendToken)
        assertEquals("1000000000", grant.capBaseUnits)
        assertEquals(0L, grant.windowStartEpochSeconds)
        assertEquals(windowEnd, grant.windowEndEpochSeconds)
    }
}
