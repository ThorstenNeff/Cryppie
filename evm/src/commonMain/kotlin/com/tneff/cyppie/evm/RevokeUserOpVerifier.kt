package com.tneff.cyppie.evm

/** Thrown when a revoke userOp fails ANY check — the app must NOT owner-sign it (fail-closed). */
class RevokeVerificationException(message: String) : Exception(message)

/**
 * On-device verifier for the **revoke userOp** (`removeSession`) the backend returns from `/v1/userop/build`
 * (KAN-157). Like the enable, the revoke is owner-signed via the Kernel ROOT validator — **full account
 * authority** — so signing without verifying the op is a blind-sign / drain. [verify] binds the signature to the
 * op and proves the op is EXACTLY one `removeSession(expectedPermissionId)` call on the SmartSessions module:
 *
 *  1. recompute `userOpHash` ([Erc4337UserOp]) and assert `digestToSign == hashMessage(userOpHash)` (binding);
 *  2. `sender == expectedAccount` (the follower SCA) and `initCode == "0x"` (7702 same-address, no factory);
 *  3. the callData is a Kernel SINGLE-call execute of EXACTLY `removeSession(expectedPermissionId)` on the
 *     pinned SmartSessions module — **byte-equal** to the expected call (rebuild + compare, no decoder gap).
 *
 * Targets/selector/module are client-pinned constants, never read from the backend payload.
 */
object RevokeUserOpVerifier {

    const val SMART_SESSIONS_ADDRESS: String = SmartSessionEnableDigest.SMART_SESSION_ADDRESS
    const val REMOVE_SESSION_SELECTOR: String = "0xf867b08e" // removeSession(bytes32 permissionId)

    data class VerifiedRevoke(val account: String, val chainId: Long, val permissionId: String, val userOpHash: ByteArray)

    /** `removeSession(permissionId)` calldata = selector ‖ the 32-byte permissionId. */
    fun removeSessionCallData(permissionId: String): ByteArray {
        val id = Hex.decodeOrNull(permissionId.removePrefix("0x").removePrefix("0X"))
            ?: throw RevokeVerificationException("bad permissionId hex: $permissionId")
        require(id.size == 32) { "permissionId must be 32 bytes: $permissionId" }
        return (Hex.decodeOrNull(REMOVE_SESSION_SELECTOR.removePrefix("0x")) ?: ByteArray(0)) + id
    }

    fun verify(
        userOp: Erc4337UserOp.PackedUserOp,
        digestToSign: String,
        chainId: Long,
        expectedAccount: String,
        expectedPermissionId: String,
    ): VerifiedRevoke {
        val userOpHash = Erc4337UserOp.userOpHash(userOp, chainId)
        val expectedDigest = "0x" + Hex.encode(Erc4337UserOp.digestToSign(userOpHash))
        if (!digestToSign.equals(expectedDigest, ignoreCase = true)) {
            throw RevokeVerificationException("digestToSign does not bind the recomputed userOpHash")
        }
        if (!userOp.sender.equals(expectedAccount, ignoreCase = true)) {
            throw RevokeVerificationException("userOp.sender != expectedAccount")
        }
        if (userOp.initCode != "0x") {
            throw RevokeVerificationException("unexpected initCode/factory — only the 7702 same-address account is allowed")
        }
        val call = try {
            KernelExecuteBatch.decodeSingle(userOp.callData)
        } catch (e: Exception) {
            throw RevokeVerificationException("callData is not a Kernel single-call execute: ${e.message}")
        }
        if (!call.target.equals(SMART_SESSIONS_ADDRESS, ignoreCase = true)) {
            throw RevokeVerificationException("revoke call target != SmartSessions module")
        }
        val expectedCall = removeSessionCallData(expectedPermissionId)
        if (!Hex.encode(call.callData).equals(Hex.encode(expectedCall), ignoreCase = true)) {
            throw RevokeVerificationException("call is not removeSession(expectedPermissionId)")
        }
        return VerifiedRevoke(expectedAccount, chainId, expectedPermissionId, userOpHash)
    }
}
