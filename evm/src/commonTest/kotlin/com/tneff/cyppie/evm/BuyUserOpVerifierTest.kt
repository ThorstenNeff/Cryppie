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

    @Test
    fun byteExact_vsBackendDcaBuyVector_bothChains() {
        // Backend dca-buy-userop-vector.mjs (`6a0ac84`): real approve+multicall callData, USE-mode lane nonce.
        val realBuy = Erc4337UserOp.PackedUserOp(
            sender = account,
            nonce = "0x100000000008bdaba73cd9815d79069c247eb4bda00000000000000000000",
            initCode = "0x", callData = VECTOR_CALLDATA,
            accountGasLimits = "0x0000000000000000000000000016e360000000000000000000000000000927c0",
            preVerificationGas = "0x30d40",
            gasFees = "0x0000000000000000000000003b9aca000000000000000000000000003b9aca00",
            paymasterAndData = "0x0000000000000039cd5e8aE05257CE51C473ddd100000000000000000000000000061a80000000000000000000000000000186a0",
        )
        val ethHash = "0x10a9d27aeff1a9c43ec52f7f478e69fa553fb71f8a6565c55fde969d3a89331f"
        assertEquals(ethHash.removePrefix("0x"), Hex.encode(Erc4337UserOp.userOpHash(realBuy, 1L)))
        assertEquals(usdc, BuyUserOpVerifier.verify(realBuy, ethHash, 1L, account, usdc, router, swapSelector).spendToken)
        val baseHash = "0x37b67188d3f56d6a9f9ca09a9b16fbb21fce28a2ca68668e910fd8af2e63947b"
        assertEquals(baseHash.removePrefix("0x"), Hex.encode(Erc4337UserOp.userOpHash(realBuy, 8453L)))
        assertEquals(router, BuyUserOpVerifier.verify(realBuy, baseHash, 8453L, account, usdc, router, swapSelector).router)
    }

    private companion object {
        const val APPROVE = "0x095ea7b3"
        const val VECTOR_CALLDATA = "0xe9ae5c530100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000003a00000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000120000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000044095ea7b300000000000000000000000068b3465833fb72a70ecdf485e0e4c7bd8665fc4500000000000000000000000000000000000000000000000000000000000f42400000000000000000000000000000000000000000000000000000000000000000000000000000000068b3465833fb72a70ecdf485e0e4c7bd8665fc450000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000001a45ae401dc0000000000000000000000000000000000000000000000000000000070dbd88000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000e404e45aaf000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48000000000000000000000000c02aaa39b223fe8d0a0e5c4f27ead9083c756cc200000000000000000000000000000000000000000000000000000000000001f4000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb9226600000000000000000000000000000000000000000000000000000000000f4240000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
    }
}
