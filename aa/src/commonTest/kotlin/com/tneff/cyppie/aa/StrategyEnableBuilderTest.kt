package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.GrantVerificationException
import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-165 — structure-first test of the Strategy grant (Vaults-B): `StrategyEnableBuilder` builds the M-cap
 * session and `verifyBasketGrant` round-trips it (the SELL-cap set is on-chain-pinned; the swap tokenOut/weights
 * are advisory). Byte-pin against the backend KAN-164 vector is a follow-up (like the Copy/DCA KAT).
 */
class StrategyEnableBuilderTest {

    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val sessionPubkey = "0x489ccacAC8836C71Ad5B20Bf61e0b885425b227e"
    private val salt = "0x00000000000000000000000000000000000000000000000000000000000000aa"
    private val weth = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
    private val usdc = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    private val wbtc = "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599"
    private val urEth = StrategyEnableBuilder.universalRouter(1L)!!
    private val permit2 = StrategyEnableBuilder.PERMIT2
    private val window = 0L to 1893456000L

    private val caps = listOf(StrategyCap(weth, "1000000000000000000"), StrategyCap(usdc, "1000000000"))

    private fun built() = StrategyEnableBuilder.build(
        chainId = 1L, account = account, sessionPublicKey = sessionPubkey,
        caps = caps, windowStart = window.first, windowEnd = window.second, salt = salt, nonce = 0L,
    )

    private fun verify(permissions: com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions, capTokens: Set<String>, digest: String) =
        SmartSessionGrantVerifier.verifyBasketGrant(
            account = account, chainId = 1L, sessionValidator = SmartSessionGrantVerifier.SESSION_VALIDATOR,
            sessionValidatorInitData = DcaEnableBuilder.ownableInitData(sessionPubkey), salt = salt, nonce = "0",
            permissions = permissions, digestToSign = digest,
            swapTarget = urEth, swapSelector = SmartSessionGrantVerifier.UNIVERSAL_ROUTER_EXECUTE_SELECTOR,
            expectedCapTokens = capTokens,
            infraActions = listOf(SmartSessionGrantVerifier.ActionPin(permit2, SmartSessionGrantVerifier.PERMIT2_APPROVE_SELECTOR)),
        )

    @Test
    fun buildVerifyRoundTrip_returnsTheMCapBasket() {
        val b = built()
        val grant = verify(b.permissions, setOf(weth, usdc), b.digestToSign)
        assertEquals(urEth, grant.actionTarget) // the UR is the user-facing swap target
        assertEquals(2, grant.caps.size)
        assertEquals(setOf(weth.lowercase(), usdc.lowercase()), grant.caps.map { it.token.lowercase() }.toSet())
        assertEquals("1000000000000000000", grant.caps.first { it.token.equals(weth, true) }.capBaseUnits)
        assertEquals(1893456000L, grant.windowEndEpochSeconds)
    }

    @Test
    fun permissionId_isStableForTheTokenSet() {
        assertEquals(built().permissionId, built().permissionId)
    }

    @Test
    fun failsClosed_onUnexpectedCapToken() {
        // verify against a basket set that omits one of the actually-capped tokens → fail-closed.
        assertFailsWith<GrantVerificationException> { verify(built().permissions, setOf(weth, wbtc), built().digestToSign) }
    }

    @Test
    fun failsClosed_onMissingCapToken() {
        // expect 3 tokens but the session only caps 2 → fail-closed.
        assertFailsWith<GrantVerificationException> { verify(built().permissions, setOf(weth, usdc, wbtc), built().digestToSign) }
    }

    @Test
    fun failsClosed_onDuplicateCap() {
        val p = built().permissions
        val dup = ActionData(
            DcaEnableBuilder.APPROVE_SELECTOR, weth,
            listOf(PolicyData(DcaEnableBuilder.SPENDING_LIMIT_POLICY, DcaEnableBuilder.spendingLimitInitData(weth, "5"))),
        )
        val tampered = p.copy(actions = p.actions + dup)
        val digest = "0x" + com.tneff.cyppie.evm.Hex.encode(
            com.tneff.cyppie.evm.SmartSessionEnableDigest.enableDigest(
                account, 1L, SmartSessionGrantVerifier.SESSION_VALIDATOR, DcaEnableBuilder.ownableInitData(sessionPubkey), salt, "0", tampered,
            ),
        )
        assertFailsWith<GrantVerificationException> { verify(tampered, setOf(weth, usdc), digest) }
    }

    @Test
    fun failsClosed_onRogueExtraAction() {
        val p = built().permissions
        val rogue = ActionData("0x3593564c", "0x000000000000000000000000000000000000bEEF", listOf(p.userOpPolicies.first()))
        val tampered = p.copy(actions = p.actions + rogue)
        val digest = "0x" + com.tneff.cyppie.evm.Hex.encode(
            com.tneff.cyppie.evm.SmartSessionEnableDigest.enableDigest(
                account, 1L, SmartSessionGrantVerifier.SESSION_VALIDATOR, DcaEnableBuilder.ownableInitData(sessionPubkey), salt, "0", tampered,
            ),
        )
        assertFailsWith<GrantVerificationException> { verify(tampered, setOf(weth, usdc), digest) }
    }
}
