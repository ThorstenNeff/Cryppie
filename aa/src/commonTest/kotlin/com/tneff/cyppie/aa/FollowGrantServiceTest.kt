package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class RecordingSeed(seed: ByteArray) : SeedSource, AutoCloseable {
    private val delegate = SeedSource.ofSeed(seed)
    var closed = false
    override fun <R> withSeed(block: (ByteArray) -> R): R = delegate.withSeed(block)
    override fun close() { closed = true }
}

/** A scriptable CopyApi over the canonical Copy-ETH enable-userOp KAT (= enable-userop-vector.mjs). */
private class FakeCopyApi(
    private val prepare: CopyPrepare,
    private val built: BuiltEnableUserOp,
    private val statuses: ArrayDeque<OpStatus>,
) : CopyApi {
    var submittedSignature: String? = null
    var submittedAuthorization: SignedAuthorization? = null
    var grantedPermissionId: String? = null
    override suspend fun prepare(request: CopyScopeRequest) = prepare
    override suspend fun buildEnableUserOp(request: BuildEnableRequest) = built
    override suspend fun submitEnableUserOp(request: SubmitEnableRequest): String {
        submittedSignature = request.signature
        submittedAuthorization = request.signedAuthorization
        return request.userOpHash
    }
    override suspend fun opStatus(chainId: Long, userOpHash: String) = statuses.removeFirst()
    override suspend fun grantSession(request: CopyGrantRequest) { grantedPermissionId = request.permissionId }
    override suspend fun listCopySessions(): List<CopySession> = emptyList()
}

/** End-to-end orchestration test for the Copy grant (KAN-154) over the byte-exact enable-userOp vector. */
class FollowGrantServiceTest {

    private val seed = Mnemonic.of("test test test test test test test test test test test junk").toSeed()
    private val owner = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266") // follower SCA = Hardhat acct0

    private val prepare = CopyPrepare(
        permissionId = "0x1c3f76fac3f146c12a665114ff61d6d257653434d854ecb3570c6b2c32e96b55",
        chainId = 1L, follower = owner.value,
        sessionPublicKey = "0x489ccacAC8836C71Ad5B20Bf61e0b885425b227e",
        token = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", capTotalBudget = "1000000000",
        windowStart = 0L, windowEnd = 1893456000L,
        salt = "0x00000000000000000000000000000000000000000000000000000000000000aa",
        nonce = 0L, source = "0x1111111111111111111111111111111111111111", allocationBps = 1000,
    )

    private val scope = CopyScopeRequest(
        chainId = 1L, source = "0x1111111111111111111111111111111111111111",
        capTotalBudget = "1000000000", token = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", follower = owner.value,
        router = "0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af", selector = "0x3593564c",
        windowStart = 0L, windowEnd = 1893456000L,
        tokenOut = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", feeTier = 500, allocationBps = 1000,
    )

    private val builtUserOp = BuiltEnableUserOp(
        userOp = UnpackedUserOp(
            sender = owner.value, nonce = "0x0", callData = CALLDATA_ETH,
            callGasLimit = "600000", verificationGasLimit = "1500000", preVerificationGas = "200000",
            maxFeePerGas = "1000000000", maxPriorityFeePerGas = "1000000000",
            paymaster = "0x0000000000000039cd5e8aE05257CE51C473ddd1",
            paymasterVerificationGasLimit = "400000", paymasterPostOpGasLimit = "100000", paymasterData = "0x",
        ),
        userOpHash = "0xc4d1510e7efadce1ca010db75fe501d2ae685f14b8c27c4fe364ed1d0170dfe9",
        digestToSign = "0xe7d07e62aba236625113c51e2c9b24b7cda6ff8d23157f3ae9f9d793e1e4e118",
    )

    private val permissionId = "0x1c3f76fac3f146c12a665114ff61d6d257653434d854ecb3570c6b2c32e96b55"

    @Test
    fun prepareGrant_disclosesVerifiedScope_andAdvisoryContext() = runTest {
        val svc = FollowGrantService(FakeCopyApi(prepare, builtUserOp, ArrayDeque()))
        val preview = svc.prepareGrant(scope, owner)
        // crypto-verified scope (rendered as the authorization):
        assertEquals("0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af", preview.verifiedGrant.actionTarget) // UR ETH
        assertEquals("1000000000", preview.verifiedGrant.capBaseUnits)
        assertEquals(1893456000L, preview.verifiedGrant.windowEndEpochSeconds)
        // advisory context (must be rendered separately by the UI):
        assertEquals("0x1111111111111111111111111111111111111111", preview.source)
        assertEquals(1000, preview.allocationBps)
        assertEquals(permissionId, preview.enable.permissionId)
    }

    @Test
    fun authorizeGrant_verifies_signs_submits_polls_grants() = runTest {
        val api = FakeCopyApi(prepare, builtUserOp, ArrayDeque(listOf(OpStatus("pending"), OpStatus("included", "0xtx"))))
        val svc = FollowGrantService(api)
        val preview = svc.prepareGrant(scope, owner)
        val seedSource = RecordingSeed(seed)

        val result = svc.authorizeGrant(preview, seedSource, maxPollAttempts = 5, pollDelayMs = 0)

        assertEquals(permissionId, result.permissionId)
        assertEquals("0xtx", result.txHash)
        assertEquals(permissionId, api.grantedPermissionId) // session marked active only after inclusion
        assertTrue(api.submittedSignature!!.startsWith("0x") && api.submittedSignature!!.length == 132) // 65-byte owner sig
        assertTrue(seedSource.closed, "seed zeroized after signing")
    }

    @Test
    fun authorizeGrant_firstEnable_verifiesAndSignsThe7702Authorization() = runTest {
        val firstEnable = builtUserOp.copy(
            authorizationToSign = AuthorizationTuple(chainId = 1L, address = "0xd6CEDDe84be40893d153Be9d467CD6aD37875b28", nonce = 0L),
        )
        val api = FakeCopyApi(prepare, firstEnable, ArrayDeque(listOf(OpStatus("included", "0xtx"))))
        val svc = FollowGrantService(api)
        val preview = svc.prepareGrant(scope, owner)

        val result = svc.authorizeGrant(preview, RecordingSeed(seed), maxPollAttempts = 2, pollDelayMs = 0)

        assertEquals(permissionId, result.permissionId)
        val auth = api.submittedAuthorization!!
        assertEquals("0xd6CEDDe84be40893d153Be9d467CD6aD37875b28", auth.address)
        assertEquals(1L, auth.chainId)
        assertTrue(auth.yParity == 0 || auth.yParity == 1)
        assertEquals(66, auth.r.length) // 0x + 32 bytes
    }

    @Test
    fun authorizeGrant_firstEnable_refusesRogueDelegateTarget() = runTest {
        val rogue = builtUserOp.copy(
            authorizationToSign = AuthorizationTuple(1L, "0x000000000000000000000000000000000000dEaD", 0L),
        )
        val api = FakeCopyApi(prepare, rogue, ArrayDeque())
        val svc = FollowGrantService(api)
        val preview = svc.prepareGrant(scope, owner)
        val seedSource = RecordingSeed(seed)
        assertFailsWith<com.tneff.cyppie.evm.AuthorizationVerificationException> {
            svc.authorizeGrant(preview, seedSource, maxPollAttempts = 1, pollDelayMs = 0)
        }
        assertEquals(null, api.submittedSignature) // never signed/submitted a rogue delegation
        // P1 (ADR-0009): the seed is zeroized even when verify7702 rejects BEFORE signing (verify-before-sign leak).
        assertTrue(seedSource.closed, "seed must be zeroized even when a pre-sign verify throws")
    }

    @Test
    fun authorizeGrant_zeroizesSeed_whenBuildNetcallFails() = runTest {
        val api = object : CopyApi by FakeCopyApi(prepare, builtUserOp, ArrayDeque()) {
            override suspend fun buildEnableUserOp(request: BuildEnableRequest): BuiltEnableUserOp =
                throw IllegalStateException("network down")
        }
        val svc = FollowGrantService(api)
        val preview = svc.prepareGrant(scope, owner)
        val seedSource = RecordingSeed(seed)
        assertFailsWith<IllegalStateException> { svc.authorizeGrant(preview, seedSource, maxPollAttempts = 1, pollDelayMs = 0) }
        assertTrue(seedSource.closed, "seed must be zeroized even when the /build netcall fails before signing")
    }

    @Test
    fun prepareGrant_refusesBackendFollowerMismatch() = runTest {
        val rogue = prepare.copy(follower = "0x000000000000000000000000000000000000dEaD")
        val svc = FollowGrantService(FakeCopyApi(rogue, builtUserOp, ArrayDeque()))
        assertFailsWith<IllegalArgumentException> { svc.prepareGrant(scope, owner) }
    }

    @Test
    fun authorizeGrant_throwsOnRevert() = runTest {
        val api = FakeCopyApi(prepare, builtUserOp, ArrayDeque(listOf(OpStatus("failed"))))
        val svc = FollowGrantService(api)
        val preview = svc.prepareGrant(scope, owner)
        assertFailsWith<IllegalStateException> { svc.authorizeGrant(preview, RecordingSeed(seed), maxPollAttempts = 3, pollDelayMs = 0) }
        assertEquals(null, api.grantedPermissionId) // never granted on a revert
    }

    private companion object {
        const val CALLDATA_ETH = "0xe9ae5c53010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000ae00000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000260000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb922660000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000001849517e29f000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000008bdaba73cd9815d79069c247eb4bda000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000f400000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004e9ae5c53000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008bdaba73cd9815d79069c247eb4bda0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000007a421712407000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000013fdb5234e4e3162a810f54d9f7e9800000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000aa0000000000000000000000000000000000000000000000000000000000000180000000000000000000000000000000000000000000000000000000000000024000000000000000000000000000000000000000000000000000000000000002c000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000001000000000000000000000000489ccacac8836c71ad5b20bf61e0b885425b227e000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000d30f611fa3bf652ac68794285869300000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000c000070dbd880000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000002200000000000000000000000000000000000000000000000000000000000000340095ea7b300000000000000000000000000000000000000000000000000000000000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000033212e272655d8a22402db819477a6000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000001000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb480000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000003b9aca0087517c4500000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022d473030f116ddee9f6b43ac78ba30000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000d30f611fa3bf652ac68794285869300000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000c000070dbd88000000000000000000000000000000000000000000000000000003593564c0000000000000000000000000000000000000000000000000000000000000000000000000000000066a9893cc07d91d95644aedd05d03f95e1dba8af0000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000d30f611fa3bf652ac68794285869300000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000c000070dbd880000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
    }
}
