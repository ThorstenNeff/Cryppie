package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.RevokeUserOpVerifier
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class RevokeSeed(seed: ByteArray) : SeedSource, AutoCloseable {
    private val delegate = SeedSource.ofSeed(seed)
    var closed = false
    override fun <R> withSeed(block: (ByteArray) -> R): R = delegate.withSeed(block)
    override fun close() { closed = true }
}

private class FakeRevokeApi(private val built: BuiltEnableUserOp) : EnableBroadcastApi {
    var calls: List<EnableCall>? = null
    override suspend fun buildEnableUserOp(request: BuildEnableRequest): BuiltEnableUserOp { calls = request.calls; return built }
    override suspend fun submitEnableUserOp(request: SubmitEnableRequest) = request.userOpHash
    override suspend fun opStatus(chainId: Long, userOpHash: String) = OpStatus("included", "0xtx")
}

/** KAN-157 — RevokeBroadcaster runs the same Approach-(a)/seed-hygiene flow as enable, over the revoke vector. */
class RevokeBroadcasterTest {

    private val seed = Mnemonic.of("test test test test test test test test test test test junk").toSeed()
    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val permissionId = "0x1c3f76fac3f146c12a665114ff61d6d257653434d854ecb3570c6b2c32e96b55"

    private val built = BuiltEnableUserOp(
        userOp = UnpackedUserOp(
            sender = account, nonce = "0x0", callData = REVOKE_CALLDATA,
            callGasLimit = "300000", verificationGasLimit = "600000", preVerificationGas = "100000",
            maxFeePerGas = "1000000000", maxPriorityFeePerGas = "1000000000",
            paymaster = "0x0000000000000039cd5e8aE05257CE51C473ddd1",
            paymasterVerificationGasLimit = "300000", paymasterPostOpGasLimit = "100000", paymasterData = "0x",
        ),
        userOpHash = "0x5fbcdaa3ed2713784493834ce101458ebdd0e1f1d13ec757aa9e6e7cd8f24bc0",
        digestToSign = "0x955f183aea16d3003d25406bed8bf4b835eef026b096506b30745fcd011f4ea9",
    )

    @Test
    fun revoke_verifiesSignsSubmits_andZeroizes() = runTest {
        val api = FakeRevokeApi(built)
        val seedSource = RevokeSeed(seed)
        val result = RevokeBroadcaster().revoke(api, ExpectedRevoke(1L, account, permissionId), seedSource, maxPollAttempts = 2, pollDelayMs = 0)
        assertEquals("0x5fbcdaa3ed2713784493834ce101458ebdd0e1f1d13ec757aa9e6e7cd8f24bc0", result.userOpHash)
        assertEquals(1, api.calls!!.size) // exactly one call: removeSession on SmartSessions
        assertEquals(RevokeUserOpVerifier.SMART_SESSIONS_ADDRESS.lowercase(), api.calls!![0].to.lowercase())
        assertTrue(seedSource.closed, "seed zeroized after signing")
    }

    @Test
    fun revoke_failsClosed_onPermissionIdMismatch_andZeroizes() = runTest {
        val seedSource = RevokeSeed(seed)
        // expected != the op's removeSession permissionId → verifyRevokeUserOp throws BEFORE signing.
        assertFailsWith<com.tneff.cyppie.evm.RevokeVerificationException> {
            RevokeBroadcaster().revoke(
                FakeRevokeApi(built),
                ExpectedRevoke(1L, account, "0x0000000000000000000000000000000000000000000000000000000000000bad"),
                seedSource, maxPollAttempts = 1, pollDelayMs = 0,
            )
        }
        assertTrue(seedSource.closed, "seed zeroized even when the revoke verify rejects before signing")
    }

    private companion object {
        const val REVOKE_CALLDATA = "0xe9ae5c5300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000005800000000008bDABA73cD9815d79069c247Eb4bDA0000000000000000000000000000000000000000000000000000000000000000f867b08e1c3f76fac3f146c12a665114ff61d6d257653434d854ecb3570c6b2c32e96b550000000000000000"
    }
}
