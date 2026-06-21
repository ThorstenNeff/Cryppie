package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.Eip7702Authorization
import com.tneff.cyppie.evm.EnableUserOpVerifier
import com.tneff.cyppie.evm.Erc4337UserOp
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.delay

/**
 * The **shared** on-device enable-broadcast orchestration (KAN-154 Copy + KAN-159 DCA). Given an
 * [EnableBroadcastApi] and the on-device-built [ExpectedEnable] (Copy via `CopyEnableBuilder`, DCA via
 * `DcaEnableBuilder` — the only difference is the session-config), it runs the SAME secured chain so the
 * full-authority signatures are never blind:
 *
 *  1. build the enable userOp (backend), pack it, and **[EnableUserOpVerifier.verify]** it binds + proves the op
 *     is exactly install(SmartSessions)+enableSessions(OUR session) — recompute, digest-bind, structure, content;
 *  2. on the first enable, **[Eip7702Authorization.verify]** pins the delegate-target before signing (KAN-160);
 *  3. owner-sign both digests in ONE seed window ([AaSigner.signDigests]); submit; poll for inclusion.
 *
 * Throws on any verification failure (do NOT sign) or revert/timeout. The caller marks the session
 * granted/registered after a successful [EnableBroadcastResult].
 */
class EnableBroadcaster(private val aaSigner: AaSigner = AaSigner()) {

    suspend fun broadcast(
        api: EnableBroadcastApi,
        buildRequest: BuildEnableRequest,
        expected: ExpectedEnable,
        seedSource: SeedSource,
        maxPollAttempts: Int = 30,
        pollDelayMs: Long = 2_000,
    ): EnableBroadcastResult {
        val owner = EvmAddress.parse(expected.account)
        // 🔒 P1 (ADR-0009): the broadcaster OWNS the seed for the whole build→verify→sign window. The decrypting
        // verify-before-sign work (the /build netcall + verifyEnableUserOp + verify7702) runs BEFORE signing and is
        // attacker-triggerable to throw — so the seed MUST be zeroized on EVERY exit path, not just the sign path.
        // `use` closes it on normal return AND on any throw; signDigests is told NOT to close (closeSeed=false), so
        // there is exactly ONE close (here), no double-close, and the seed window is the minimum (closed right after
        // signing — submit/poll below never touch it).
        val closeable = seedSource as? AutoCloseable
            ?: throw IllegalArgumentException("seedSource must be zeroizable (AutoCloseable) — refusing to risk a key leak")
        val signed = closeable.use {
            val built = api.buildEnableUserOp(buildRequest)
            val packed = with(built.userOp) {
                Erc4337UserOp.pack(
                    sender = sender, nonce = nonce, callData = callData,
                    callGasLimit = callGasLimit, verificationGasLimit = verificationGasLimit, preVerificationGas = preVerificationGas,
                    maxFeePerGas = maxFeePerGas, maxPriorityFeePerGas = maxPriorityFeePerGas,
                    factory = factory, factoryData = factoryData, paymaster = paymaster,
                    paymasterVerificationGasLimit = paymasterVerificationGasLimit,
                    paymasterPostOpGasLimit = paymasterPostOpGasLimit, paymasterData = paymasterData,
                )
            }
            // P0 — bind the owner's (full-authority root) signature to an op that enables EXACTLY our session.
            EnableUserOpVerifier.verify(
                userOp = packed, digestToSign = built.digestToSign, chainId = expected.chainId, expectedAccount = owner.value,
                sessionValidator = expected.sessionValidator, sessionValidatorInitData = expected.sessionValidatorInitData,
                salt = expected.salt, permissions = expected.permissions,
            )
            // P0 (KAN-160) — on the first enable, pin the 7702 delegate-target + chain before signing the delegation.
            val auth = built.authorizationToSign
            val authDigest = auth?.let { Eip7702Authorization.verify(it.chainId, it.address, it.nonce, expected.chainId) }
            val userOpDigest = Hex.decodeOrNull(built.digestToSign.removePrefix("0x"))
                ?: throw IllegalArgumentException("digestToSign not valid hex")
            val digests = if (authDigest != null) listOf(authDigest, userOpDigest) else listOf(userOpDigest)
            val signatures = aaSigner.signDigests(digests, owner, seedSource, closeSeed = false)
            SignedEnable(built.userOpHash, auth, signatures)
        }
        // seed is now zeroized; submit + poll use only the signatures.
        val signedAuthorization = signed.auth?.let { tuple -> signedAuthorization(tuple, signed.signatures[0]) }
        val userOpHash = api.submitEnableUserOp(
            SubmitEnableRequest(signed.userOpHash, signed.signatures.last(), signedAuthorization),
        )

        repeat(maxPollAttempts) {
            val status = api.opStatus(expected.chainId, userOpHash)
            when (status.status) {
                "included" -> return EnableBroadcastResult(userOpHash, status.txHash)
                "failed" -> throw IllegalStateException("enable userOp reverted on-chain: $userOpHash")
            }
            delay(pollDelayMs)
        }
        throw IllegalStateException("enable userOp not included after $maxPollAttempts polls: $userOpHash")
    }

    private class SignedEnable(val userOpHash: String, val auth: AuthorizationTuple?, val signatures: List<String>)

    /** Splits a 65-byte `r‖s‖v` signature into the EIP-7702 [SignedAuthorization] (yParity = v − 27). */
    private fun signedAuthorization(tuple: AuthorizationTuple, signatureHex: String): SignedAuthorization {
        val sig = Hex.decodeOrNull(signatureHex.removePrefix("0x"))
            ?: throw IllegalStateException("authorization signature not valid hex")
        require(sig.size == 65) { "expected a 65-byte signature, got ${sig.size}" }
        val v = sig[64].toInt() and 0xFF
        return SignedAuthorization(
            chainId = tuple.chainId, address = tuple.address, nonce = tuple.nonce,
            r = "0x" + Hex.encode(sig.copyOfRange(0, 32)),
            s = "0x" + Hex.encode(sig.copyOfRange(32, 64)),
            yParity = v - 27,
        )
    }
}

data class EnableBroadcastResult(val userOpHash: String, val txHash: String?)
