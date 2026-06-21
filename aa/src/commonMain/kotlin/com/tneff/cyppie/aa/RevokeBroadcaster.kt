package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.RevokeUserOpVerifier
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.delay

/** The session to revoke — [account] is the device owner (7702 same-address); [permissionId] is the follow handle. */
data class ExpectedRevoke(val chainId: Long, val account: String, val permissionId: String)

/**
 * The shared on-device **revoke** broadcaster (KAN-157) — the sibling of [EnableBroadcaster] for the
 * `removeSession` userOp (Dev-1's revoke UI consumes it). Same Approach-(a) flow + same seed hygiene: the app
 * builds the single removeSession call on-device, the generic keyless `/build` wraps it, the app re-verifies it
 * byte-exact ([RevokeUserOpVerifier]) and owner-signs the bound digest, the backend submits. No 7702 authorization
 * (the account is already upgraded by the time a session can be revoked).
 *
 * 🔒 The broadcaster OWNS the seed across build→verify→sign (`closeable.use{}` zeroizes on EVERY path — verify-throw,
 * netcall-fail, success), `signDigests(closeSeed=false)` so there is exactly one close (no double-close). The
 * owner's ROOT-validator signature is bound to an op that is EXACTLY `removeSession(expectedPermissionId)` — a
 * tampered permissionId / extra call / wrong target fails closed before signing.
 */
class RevokeBroadcaster(private val aaSigner: AaSigner = AaSigner()) {

    suspend fun revoke(
        api: EnableBroadcastApi,
        expected: ExpectedRevoke,
        seedSource: SeedSource,
        maxPollAttempts: Int = 30,
        pollDelayMs: Long = 2_000,
    ): EnableBroadcastResult {
        val owner = EvmAddress.parse(expected.account)
        val closeable = seedSource as? AutoCloseable
            ?: throw IllegalArgumentException("seedSource must be zeroizable (AutoCloseable) — refusing to risk a key leak")
        val signed = closeable.use {
            val buildRequest = BuildEnableRequest(
                chainId = expected.chainId, owner = expected.account,
                calls = listOf(
                    EnableCall(
                        to = RevokeUserOpVerifier.SMART_SESSIONS_ADDRESS, value = "0",
                        data = "0x" + Hex.encode(RevokeUserOpVerifier.removeSessionCallData(expected.permissionId)),
                    ),
                ),
            )
            val built = api.buildEnableUserOp(buildRequest)
            // P0 — bind the owner's (full-authority root) signature to EXACTLY removeSession(expectedPermissionId).
            RevokeUserOpVerifier.verify(
                userOp = built.userOp.toPackedUserOp(), digestToSign = built.digestToSign,
                chainId = expected.chainId, expectedAccount = owner.value, expectedPermissionId = expected.permissionId,
            )
            val userOpDigest = Hex.decodeOrNull(built.digestToSign.removePrefix("0x"))
                ?: throw IllegalArgumentException("digestToSign not valid hex")
            val signature = aaSigner.signDigests(listOf(userOpDigest), owner, seedSource, closeSeed = false).first()
            built.userOpHash to signature
        }
        val userOpHash = api.submitEnableUserOp(SubmitEnableRequest(signed.first, signed.second, null))

        repeat(maxPollAttempts) {
            val status = api.opStatus(expected.chainId, userOpHash)
            when (status.status) {
                "included" -> return EnableBroadcastResult(userOpHash, status.txHash)
                "failed" -> throw IllegalStateException("revoke userOp reverted on-chain: $userOpHash")
            }
            delay(pollDelayMs)
        }
        throw IllegalStateException("revoke userOp not included after $maxPollAttempts polls: $userOpHash")
    }
}
