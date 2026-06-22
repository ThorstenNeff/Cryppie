package com.tneff.cyppie.evm

/** Thrown when a DCA buy userOp fails ANY check — the app must NOT sign it (fail-closed). */
class BuyVerificationException(message: String) : Exception(message)

/**
 * On-device verifier for the **DCA buy userOp** (USE-mode) the backend builds each scheduled buy (the no-blind
 * counterpart to [EnableUserOpVerifier], for the on-device buy-sign handshake). The buy is signed by the SESSION
 * key (the SmartSession's OwnableValidator), which recovers from the **RAW userOpHash** — NOT the EIP-191
 * `hashMessage` form the Kernel ROOT validator uses for the enable (the C3-lock: do not cross-wire the two paths).
 *
 *  1. recompute `userOpHash` ([Erc4337UserOp]) and assert `digestToSign == userOpHash` (RAW — the USE-mode binding);
 *  2. `sender == expectedAccount`, `initCode == "0x"`;
 *  3. the callData is the Kernel batch of EXACTLY the two buy calls — `spendToken.approve(router, …)` and
 *     `router.<swapSelector>(…)` — on the enabled DCA session's spend-token + swap-router (client-pinned at enable).
 *
 * The amounts are NOT pinned here (they are the scheduled buy size, bounded on-chain by the session's
 * SpendingLimits cap + TimeFrame window — the on-chain policy is the backstop). The verify stops blind-signing a
 * different/out-of-scope op and wasted on-chain reverts. Fail-closed on any deviation.
 */
object BuyUserOpVerifier {

    const val APPROVE_SELECTOR: String = "0x095ea7b3" // ERC-20 approve(address,uint256)

    data class VerifiedBuy(
        val account: String,
        val chainId: Long,
        val spendToken: String,
        val tokenOut: String,
        val router: String,
        val userOpHash: ByteArray,
    )

    const val EXACT_INPUT_SINGLE_SELECTOR: String = "0x04e45aaf" // SwapRouter02 exactInputSingle(ExactInputSingleParams)

    fun verify(
        userOp: Erc4337UserOp.PackedUserOp,
        digestToSign: String,
        chainId: Long,
        expectedAccount: String,
        spendToken: String,
        tokenOut: String,
        router: String,
        swapSelector: String,
    ): VerifiedBuy {
        // 1. RAW userOpHash binding (USE-mode / session-key path) — NOT hashMessage (that is the enable's root path).
        val userOpHash = Erc4337UserOp.userOpHash(userOp, chainId)
        if (!digestToSign.equals("0x" + Hex.encode(userOpHash), ignoreCase = true)) {
            throw BuyVerificationException("digestToSign != raw userOpHash (USE-mode binding)")
        }
        if (!userOp.sender.equals(expectedAccount, ignoreCase = true)) {
            throw BuyVerificationException("userOp.sender != expectedAccount")
        }
        if (userOp.initCode != "0x") {
            throw BuyVerificationException("unexpected initCode/factory — only the 7702 same-address account is allowed")
        }
        // 2. EXACTLY two calls: approve(spendToken → router) + swap on the pinned router.
        val calls = try {
            KernelExecuteBatch.decodeBatch(userOp.callData)
        } catch (e: Exception) {
            throw BuyVerificationException("callData is not a Kernel execute batch: ${e.message}")
        }
        if (calls.size != 2) throw BuyVerificationException("expected exactly 2 buy calls (approve + swap), got ${calls.size}")

        val approve = calls[0]
        if (!approve.target.equals(spendToken, ignoreCase = true)) {
            throw BuyVerificationException("approve target != spend token")
        }
        if (!KernelExecuteBatch.selectorOf(approve).equals(APPROVE_SELECTOR, ignoreCase = true)) {
            throw BuyVerificationException("call[0] is not approve()")
        }
        val spender = approveSpender(approve.callData)
        if (!spender.equals(router, ignoreCase = true)) {
            throw BuyVerificationException("approve spender != swap router")
        }

        val swap = calls[1]
        if (!swap.target.equals(router, ignoreCase = true)) {
            throw BuyVerificationException("swap target != pinned DCA router")
        }
        if (!KernelExecuteBatch.selectorOf(swap).equals(swapSelector, ignoreCase = true)) {
            throw BuyVerificationException("swap selector != pinned router function")
        }
        // 3. 🔒 Bind the swap OUTPUT token: decode multicall → exactInputSingle → tokenOut, assert == expected. The
        //    outer router/selector pin alone leaves tokenOut free → a malicious backend could churn the capped
        //    spendToken into a worthless token (the SpendingLimits cap only bounds the SELL side). For DCA the buy
        //    target is fixed by the schedule, so we pin it (pure address match, oracle-free).
        val swapTokenOut = decodeExactInputSingleTokenOut(swap.callData)
        if (!swapTokenOut.equals(tokenOut, ignoreCase = true)) {
            throw BuyVerificationException("swap tokenOut $swapTokenOut != expected $tokenOut")
        }
        return VerifiedBuy(expectedAccount, chainId, spendToken, tokenOut, router, userOpHash)
    }

    /** `approve(address spender, uint256 amount)` → the spender (low 20 bytes of the first arg word). */
    private fun approveSpender(callData: ByteArray): String {
        if (callData.size < 4 + 32) throw BuyVerificationException("approve calldata too short")
        return EvmAddress.fromBytes(callData.copyOfRange(4 + 12, 4 + 32)).value
    }

    /**
     * Decodes `multicall(uint256 deadline, bytes[] data)` (selector pinned by the caller) whose single inner call is
     * `exactInputSingle(ExactInputSingleParams)` — returns its `tokenOut` (the 2nd struct word). Fail-closed if the
     * inner call is not exactInputSingle or the encoding is malformed.
     */
    private fun decodeExactInputSingleTokenOut(multicall: ByteArray): String {
        // multicall args (after the 4-byte selector): word0 = deadline, word1 = offset to bytes[] data.
        val args = 4
        val dataOff = word(multicall, args + 32)
        val dataPos = args + dataOff
        val n = word(multicall, dataPos)
        if (n < 1) throw BuyVerificationException("multicall has no inner calls")
        val elem0 = dataPos + 32 + word(multicall, dataPos + 32) // offset to data[0], relative to the offset table
        val innerLen = word(multicall, elem0)
        val innerStart = elem0 + 32
        if (innerStart + innerLen > multicall.size || innerLen < 4 + 32 * 2) {
            throw BuyVerificationException("malformed inner swap call")
        }
        val inner = multicall.copyOfRange(innerStart, innerStart + innerLen)
        if (!("0x" + Hex.encode(inner.copyOfRange(0, 4))).equals(EXACT_INPUT_SINGLE_SELECTOR, ignoreCase = true)) {
            throw BuyVerificationException("inner swap is not exactInputSingle")
        }
        // ExactInputSingleParams = (tokenIn, tokenOut, fee, recipient, amountIn, amountOutMinimum, sqrtPriceLimitX96).
        return EvmAddress.fromBytes(inner.copyOfRange(4 + 32 + 12, 4 + 32 + 32)).value // word[1] = tokenOut
    }

    /** Reads a 32-byte word at [off] as a non-negative Int (ABI offset/length); fail-closed if it overflows. */
    private fun word(b: ByteArray, off: Int): Int {
        if (off + 32 > b.size) throw BuyVerificationException("calldata too short")
        for (i in off until off + 28) if (b[i].toInt() != 0) throw BuyVerificationException("oversized offset/length")
        var v = 0L
        for (i in off + 28 until off + 32) v = (v shl 8) or (b[i].toLong() and 0xFF)
        if (v > Int.MAX_VALUE) throw BuyVerificationException("offset/length too large")
        return v.toInt()
    }
}
