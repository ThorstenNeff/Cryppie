package com.tneff.cyppie.aa

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Byte-exact pin of the on-device-built DCA ENABLE against the backend's **real** reference **vector D**
 * (`Backend/aa-trigger/scripts/enable-vector.mjs`): emitted OwnableValidator + GLOBAL_CONSTANTS policies +
 * paymaster=true. A mismatch means the app would sign a digest the on-chain session can't validate.
 */
class DcaEnableBuilderTest {

    // Vector D literals (= enable-vector.mjs).
    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" // owner = on-device EOA = account (7702)
    private val salt = "0x0000000000000000000000000000000000000000000000000000000000000001"
    private val nonce = 0L
    private val usdc = "0xa0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    private val capBaseUnits = "1000000" // 0x0f4240
    private val windowStart = 0x683f9e80L
    private val windowEnd = 0x687a4f00L
    private val router = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"
    private val selector = "0x5ae401dc"

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
    }

    private fun built(chainId: Long): BuiltEnable {
        val config = SessionConfig(
            chainId = chainId,
            account = account,
            actions = listOf(
                ScopedAction(
                    target = router,
                    selector = selector,
                    spendingLimits = listOf(SpendingLimit(usdc, capBaseUnits)),
                    rollingWindowSeconds = 86_400L,
                    usageLimit = 30,
                    validUntil = windowEnd,
                ),
            ),
        )
        return DcaEnableBuilder.build(config, owner = account, salt = salt, nonce = nonce, windowStart = windowStart)
    }

    @Test
    fun enableDigest_matchesVectorD_chain1() {
        assertEquals("0xba3ebab8845eff4c0f5c2871bdccaecb934b9909049bd36d776386a0390a133a", built(1L).digestToSign)
    }

    @Test
    fun enableDigest_matchesVectorD_base() {
        assertEquals("0xb45d0bc89f3abd41006eab254dccc8e5d9e206a3e3c180da16dfafb719191ca8", built(8453L).digestToSign)
    }

    @Test
    fun decodeNonce_acceptsRealZero_butRejectsEmptyOrShort() {
        assertEquals(0L, DcaEnableBuilder.decodeNonce(ByteArray(32)))          // real first-session nonce 0
        assertEquals(5L, DcaEnableBuilder.decodeNonce(ByteArray(32).also { it[31] = 5 }))
        kotlin.test.assertFailsWith<IllegalStateException> { DcaEnableBuilder.decodeNonce(ByteArray(0)) }   // empty 0x → fail-closed
        kotlin.test.assertFailsWith<IllegalStateException> { DcaEnableBuilder.decodeNonce(ByteArray(31)) }  // short → fail-closed
    }

    @Test
    fun built_carriesPermissionIdAndOwnerBoundPermissions() {
        val b = built(1L)
        assertEquals("0x82bc397553fc6577974c762cd42958d860cd838a55f55f245ee5f6debab698b0", b.permissionId)
        assertEquals(true, b.permissions.permitERC4337Paymaster)
    }
}
