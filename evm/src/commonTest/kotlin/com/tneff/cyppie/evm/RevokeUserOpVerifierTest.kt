package com.tneff.cyppie.evm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-157 — byte-exact pin of the revoke userOp verifier against the backend `revoke-userop-vector.mjs` (single
 * `removeSession(permissionId)` op). Same no-blind discipline as the enable: recompute + digest-bind + the op is
 * EXACTLY one removeSession on the expected permissionId, fail-closed otherwise.
 */
class RevokeUserOpVerifierTest {

    private val sender = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val permissionId = "0x1c3f76fac3f146c12a665114ff61d6d257653434d854ecb3570c6b2c32e96b55"

    private fun op() = Erc4337UserOp.PackedUserOp(
        sender = sender, nonce = "0x0", initCode = "0x", callData = REVOKE_CALLDATA,
        accountGasLimits = "0x000000000000000000000000000927c0000000000000000000000000000493e0",
        preVerificationGas = "0x186a0",
        gasFees = "0x0000000000000000000000003b9aca000000000000000000000000003b9aca00",
        paymasterAndData = "0x0000000000000039cd5e8aE05257CE51C473ddd1000000000000000000000000000493e0000000000000000000000000000186a0",
    )

    @Test
    fun decodesSingleRemoveSessionCall() {
        val call = KernelExecuteBatch.decodeSingle(REVOKE_CALLDATA)
        assertEquals("0x00000000008bDABA73cD9815d79069c247Eb4bDA", call.target)
        assertEquals("0xf867b08e", KernelExecuteBatch.selectorOf(call))
        assertEquals(Hex.encode(RevokeUserOpVerifier.removeSessionCallData(permissionId)), Hex.encode(call.callData))
    }

    @Test
    fun userOpHashAndDigest_bothChains() {
        val hEth = Erc4337UserOp.userOpHash(op(), 1L)
        assertEquals("5fbcdaa3ed2713784493834ce101458ebdd0e1f1d13ec757aa9e6e7cd8f24bc0", Hex.encode(hEth))
        assertEquals("955f183aea16d3003d25406bed8bf4b835eef026b096506b30745fcd011f4ea9", Hex.encode(Erc4337UserOp.digestToSign(hEth)))
        val hBase = Erc4337UserOp.userOpHash(op(), 8453L)
        assertEquals("3ad2adb8a4465e9dce47d5795bb4455f31cda3d52a34d97bbb4a2424dc782ec5", Hex.encode(hBase))
        assertEquals("ccc72c94a0462389a3dc32a0c5d95f5bc2fee566766a53fca336545a4a542810", Hex.encode(Erc4337UserOp.digestToSign(hBase)))
    }

    @Test
    fun verify_roundTrips() {
        val v = RevokeUserOpVerifier.verify(
            op(), "0x955f183aea16d3003d25406bed8bf4b835eef026b096506b30745fcd011f4ea9",
            chainId = 1L, expectedAccount = sender, expectedPermissionId = permissionId,
        )
        assertEquals(permissionId, v.permissionId)
    }

    @Test
    fun verify_failsClosed_onWrongPermissionId() {
        assertFailsWith<RevokeVerificationException> {
            RevokeUserOpVerifier.verify(
                op(), "0x955f183aea16d3003d25406bed8bf4b835eef026b096506b30745fcd011f4ea9",
                1L, sender, "0x0000000000000000000000000000000000000000000000000000000000000bad",
            )
        }
    }

    @Test
    fun verify_failsClosed_onUnboundDigest() {
        assertFailsWith<RevokeVerificationException> {
            RevokeUserOpVerifier.verify(op(), "0xdead0000000000000000000000000000000000000000000000000000000000ff", 1L, sender, permissionId)
        }
    }

    private companion object {
        const val REVOKE_CALLDATA = "0xe9ae5c5300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000005800000000008bDABA73cD9815d79069c247Eb4bDA0000000000000000000000000000000000000000000000000000000000000000f867b08e1c3f76fac3f146c12a665114ff61d6d257653434d854ecb3570c6b2c32e96b550000000000000000"
    }
}
