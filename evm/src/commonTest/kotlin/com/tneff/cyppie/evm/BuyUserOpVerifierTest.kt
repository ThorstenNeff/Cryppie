package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.abi.Abi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-163 front-load — structure-first test of the DCA buy verifier (USE-mode). Builds a local buy-op fixture
 * (Kernel batch of approve + router-swap), round-trips `verify`, and pins the **C3-lock**: the RAW userOpHash
 * binds (the EIP-191 enable form is rejected). Byte-pin against the backend `buildDcaBuy` vector is a follow-up.
 */
class BuyUserOpVerifierTest {

    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val usdc = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    private val router = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"
    private val swapSelector = "0x5ae401dc"

    private fun approveCall(spender: String) =
        Abi.encodeWithSelector(APPROVE, Abi.address(spender), Abi.uint(Quantity.of(1_000_000L)))

    private fun swapCall(selector: String) =
        Hex.decodeOrNull(selector.removePrefix("0x"))!! + ByteArray(64) // selector + arbitrary multicall body

    private fun batch(calls: List<Pair<String, ByteArray>>): String {
        val execs = calls.map { (to, data) -> Abi.tuple(listOf(Abi.address(to), Abi.uint(Quantity.of(0)), Abi.bytes("0x" + Hex.encode(data)))) }
        val mode = "0x0100000000000000000000000000000000000000000000000000000000000000"
        return "0x" + Hex.encode(Abi.encodeWithSelector("0xe9ae5c53", Abi.bytes32(mode), Abi.bytes("0x" + Hex.encode(Abi.encode(Abi.array(execs))))))
    }

    private fun op(callData: String) = Erc4337UserOp.PackedUserOp(
        sender = account, nonce = "0x0", initCode = "0x", callData = callData,
        accountGasLimits = "0x0000000000000000000000000016e360000000000000000000000000000927c0",
        preVerificationGas = "0x30d40",
        gasFees = "0x0000000000000000000000003b9aca000000000000000000000000003b9aca00",
        paymasterAndData = "0x",
    )

    private val buyOp = op(batch(listOf(usdc to approveCall(router), router to swapCall(swapSelector))))
    private val rawDigest = "0x" + Hex.encode(Erc4337UserOp.userOpHash(buyOp, 1L))

    private fun verify(o: Erc4337UserOp.PackedUserOp, digest: String, token: String = usdc, r: String = router) =
        BuyUserOpVerifier.verify(o, digest, chainId = 1L, expectedAccount = account, spendToken = token, router = r, swapSelector = swapSelector)

    @Test
    fun verify_roundTrips_theBuy() {
        val v = verify(buyOp, rawDigest)
        assertEquals(usdc, v.spendToken)
        assertEquals(router, v.router)
    }

    @Test
    fun failsClosed_onEip191Form_c3Lock() {
        // The enable's hashMessage(userOpHash) form must NOT validate a buy (USE-mode binds the RAW hash).
        val enableForm = "0x" + Hex.encode(Eip191.personalSignDigest(Erc4337UserOp.userOpHash(buyOp, 1L)))
        assertFailsWith<BuyVerificationException> { verify(buyOp, enableForm) }
    }

    @Test
    fun failsClosed_onWrongSpendToken() {
        assertFailsWith<BuyVerificationException> { verify(buyOp, rawDigest, token = "0x000000000000000000000000000000000000dEaD") }
    }

    @Test
    fun failsClosed_onWrongRouter() {
        assertFailsWith<BuyVerificationException> { verify(buyOp, rawDigest, r = "0x000000000000000000000000000000000000bEEF") }
    }

    @Test
    fun failsClosed_onApproveSpenderNotRouter() {
        val bad = op(batch(listOf(usdc to approveCall("0x000000000000000000000000000000000000dEaD"), router to swapCall(swapSelector))))
        assertFailsWith<BuyVerificationException> { verify(bad, "0x" + Hex.encode(Erc4337UserOp.userOpHash(bad, 1L))) }
    }

    @Test
    fun failsClosed_onExtraCall() {
        val three = op(batch(listOf(usdc to approveCall(router), router to swapCall(swapSelector), router to swapCall(swapSelector))))
        assertFailsWith<BuyVerificationException> { verify(three, "0x" + Hex.encode(Erc4337UserOp.userOpHash(three, 1L))) }
    }

    private companion object {
        const val APPROVE = "0x095ea7b3"
    }
}
