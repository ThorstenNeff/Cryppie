package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.Erc7739Data
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import com.tneff.cyppie.evm.abi.Abi

/** Thrown when an enable userOp fails ANY check — the app must NOT owner-sign it (fail-closed). */
class EnableVerificationException(message: String) : Exception(message)

/**
 * On-device verifier for the **enable userOp** the backend returns from `/v1/userop/build` (KAN-154 Copy +
 * KAN-159 DCA, GAP-B / P1-4). The owner signs `hashMessage(userOpHash)` via the Kernel ROOT validator — i.e.
 * **full account authority** — so signing without verifying the op is a blind-sign / drain. [verify] binds the
 * signature to the op and proves the op is EXACTLY install(SmartSessions) + enableSessions(expected-session):
 *
 *  1. recompute `userOpHash` ([Erc4337UserOp]) and assert `digestToSign == hashMessage(userOpHash)` (binding);
 *  2. `sender == expectedAccount` (the follower SCA);
 *  3. the callData is the Kernel `execute` batch of EXACTLY two calls;
 *  4. call[0] = `installModule(1, SmartSessions, …)` on the account itself — **byte-equal** to the pinned install;
 *  5. call[1] = `enableSessions([session])` on the SmartSessions module — **byte-equal** to the expected session
 *     re-encoded from client-pinned fields (approach (b): rebuild + compare → no decoder-incompleteness gap).
 *
 * (4)/(5) are byte-exact rebuilds, so a tampered cap/router/window/owner/selector cannot pass. Targets/selectors/
 * module address are client-pinned constants, never read from the backend payload.
 */
object EnableUserOpVerifier {

    const val INSTALL_MODULE_SELECTOR: String = "0x9517e29f"
    const val ENABLE_SESSIONS_SELECTOR: String = "0x21712407"
    const val SMART_SESSIONS_ADDRESS: String = SmartSessionEnableDigest.SMART_SESSION_ADDRESS

    /**
     * The Kernel v3.3 `installModule(1, SmartSessions, initData)` calldata. The `initData` is fixed (the
     * SmartSessions validator install with `selectorData = 0xe9ae5c53` granting the Kernel execute selector) —
     * identical on every chain, so it is a client-pinned constant byte-compared against call[0].
     */
    const val INSTALL_MODULE_CALLDATA: String =
        // installModule(uint256 moduleTypeId=1, address module=SmartSessions, bytes initData); initData =
        // encodePacked(hook=0x..01, abi.encode(validatorData=SmartSessions-Validator.initData, hookData=0x, selectorData=0xe9ae5c53)).
        "0x9517e29f000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000008bdaba73cd9815d79069c247eb4bda000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000f400000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004e9ae5c5300000000000000000000000000000000000000000000000000000000000000000000000000000000"

    /** The verified, decoded result — the cryptographically-bound facts the app may now act on. */
    data class VerifiedEnable(val account: String, val chainId: Long, val userOpHash: ByteArray)

    /**
     * Re-encodes `enableSessions([session])` calldata for a single session from client-pinned fields (the same
     * inputs as [SmartSessionEnableDigest.enableDigest]). The on-chain `Session` struct is account-/smartSession-/
     * nonce-less (a subset of the EIP-712 `SignedSession`) and carries only `permitERC4337Paymaster` of the flags.
     */
    fun enableSessionsCallData(
        sessionValidator: String,
        sessionValidatorInitData: String,
        salt: String,
        permissions: SignedPermissions,
    ): ByteArray {
        val session = Abi.tuple(
            listOf(
                Abi.address(sessionValidator),
                Abi.bytes(sessionValidatorInitData),
                Abi.bytes32(salt),
                Abi.array(permissions.userOpPolicies.map(::policyTuple)),
                erc7739Tuple(permissions.erc7739Policies),
                Abi.array(permissions.actions.map(::actionTuple)),
                Abi.bool(permissions.permitERC4337Paymaster),
            ),
        )
        return Abi.encodeWithSelector(ENABLE_SESSIONS_SELECTOR, Abi.array(listOf(session)))
    }

    /**
     * Verifies [userOp] / [digestToSign] (the `/v1/userop/build` response) against the expected session
     * ([sessionValidator]/[sessionValidatorInitData]/[salt]/[permissions], built on-device). Returns the bound
     * facts on success; throws [EnableVerificationException] on any mismatch (fail-closed — do NOT sign).
     */
    fun verify(
        userOp: Erc4337UserOp.PackedUserOp,
        digestToSign: String,
        chainId: Long,
        expectedAccount: String,
        sessionValidator: String,
        sessionValidatorInitData: String,
        salt: String,
        permissions: SignedPermissions,
    ): VerifiedEnable {
        // 1. bind the signature to the op: recompute the hash, assert the digest is its EIP-191 form.
        val userOpHash = Erc4337UserOp.userOpHash(userOp, chainId)
        val expectedDigest = "0x" + Hex.encode(Erc4337UserOp.digestToSign(userOpHash))
        if (!digestToSign.equals(expectedDigest, ignoreCase = true)) {
            throw EnableVerificationException("digestToSign does not bind the recomputed userOpHash")
        }
        // 2. the signed op must be from the user's own account.
        if (!userOp.sender.equals(expectedAccount, ignoreCase = true)) {
            throw EnableVerificationException("userOp.sender != expectedAccount")
        }
        // 3. exactly two calls (install + enableSessions).
        val calls = try {
            KernelExecuteBatch.decodeBatch(userOp.callData)
        } catch (e: Exception) {
            throw EnableVerificationException("callData is not a Kernel execute batch: ${e.message}")
        }
        if (calls.size != 2) throw EnableVerificationException("expected exactly 2 calls, got ${calls.size}")

        // 4. call[0] = installModule(SmartSessions) on the account itself, byte-equal to the pinned install.
        val install = calls[0]
        if (!install.target.equals(expectedAccount, ignoreCase = true)) {
            throw EnableVerificationException("install call target != account")
        }
        if (!hex(install.callData).equals(INSTALL_MODULE_CALLDATA, ignoreCase = true)) {
            throw EnableVerificationException("install call is not the pinned installModule(SmartSessions)")
        }
        // 5. call[1] = enableSessions on the SmartSessions module, byte-equal to the expected session.
        val enable = calls[1]
        if (!enable.target.equals(SMART_SESSIONS_ADDRESS, ignoreCase = true)) {
            throw EnableVerificationException("enableSessions call target != SmartSessions module")
        }
        val expectedEnable = enableSessionsCallData(sessionValidator, sessionValidatorInitData, salt, permissions)
        if (!hex(enable.callData).equals(hex(expectedEnable), ignoreCase = true)) {
            throw EnableVerificationException("enableSessions calldata != expected session (cap/router/window/owner mismatch)")
        }
        return VerifiedEnable(expectedAccount, chainId, userOpHash)
    }

    private fun policyTuple(p: PolicyData): Abi.Value = Abi.tuple(listOf(Abi.address(p.policy), Abi.bytes(p.initData)))

    private fun actionTuple(a: ActionData): Abi.Value = Abi.tuple(
        listOf(
            Abi.bytes4(a.actionTargetSelector),
            Abi.address(a.actionTarget),
            Abi.array(a.actionPolicies.map(::policyTuple)),
        ),
    )

    private fun erc7739Tuple(d: Erc7739Data): Abi.Value = Abi.tuple(
        listOf(
            Abi.array(d.allowedERC7739Content.map { ctx -> Abi.tuple(listOf(Abi.bytes32(ctx.appDomainSeparator), Abi.array(ctx.contentName.map(Abi::string)))) }),
            Abi.array(d.erc1271Policies.map(::policyTuple)),
        ),
    )

    private fun hex(b: ByteArray): String = "0x" + Hex.encode(b)
}
