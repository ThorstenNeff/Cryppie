package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.evm.SmartSessionEnableDigest
import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions

/**
 * The full on-device-built Smart-Session ENABLE (PRD-05 Ph1, KAN-144 approach C). [account]/[chainId] +
 * the [permissions] + validator material + [digestToSign] — everything the grant flow needs to verify
 * ([SmartSessionGrantVerifier.verifyGrant]) and sign. There is **no backend enable endpoint**: the digest
 * is a pure offline EIP-712 ([SmartSessionEnableDigest]); the only runtime input is [nonce] (read from the
 * SmartSession module via RPC). [permissionId] keys that nonce read.
 */
data class BuiltEnable(
    val digestToSign: String,
    val account: String,
    val chainId: Long,
    val sessionValidator: String,
    val sessionValidatorInitData: String,
    val salt: String,
    val nonce: String,
    val permissionId: String,
    val permissions: SignedPermissions,
)

/**
 * Builds the Rhinestone Smart-Sessions ENABLE entirely on-device (KAN-144). The user's policy
 * ([SessionConfig]) + the **client-pinned** module/validator/policy constants → the exact `SignedPermissions`
 * + `OwnableValidator` initData → [SmartSessionEnableDigest.enableDigest] (= the backend's byte-exact vector
 * D). Nothing is taken from the backend (a spoofed address would validate). DCA authorizes the **owner key
 * itself** as the session-validator key (signer = on-device; no separate device key).
 *
 * 🔒 Pinned constants (GLOBAL_CONSTANTS, CREATE2-deterministic, identical ETH/Base) — NOT the SDK's legacy
 * top-level exports (the two-address gotcha): [OWNABLE_VALIDATOR] is `getOwnableValidator().address`, NOT
 * `OWNABLE_VALIDATOR_ADDRESS 0x2483DA…`; the policy addresses likewise come from [SmartSessionGrantVerifier].
 */
object DcaEnableBuilder {

    // All three pins are single-sourced from the verifier (KAN-144/147) — one place to update, drift-safe.
    /** Emitted OwnableValidator (GLOBAL_CONSTANTS) — the session-validator module. */
    val OWNABLE_VALIDATOR: String get() = SmartSessionGrantVerifier.SESSION_VALIDATOR
    val SPENDING_LIMIT_POLICY: String get() = SmartSessionGrantVerifier.SPENDING_LIMIT_POLICY
    val TIMEFRAME_POLICY: String get() = SmartSessionGrantVerifier.TIMEFRAME_POLICY

    /** `getNonce(bytes32 permissionId, address account)` view selector on the SmartSession module. */
    const val GET_NONCE_SELECTOR: String = "0x795f9269"
    val SMART_SESSION_ADDRESS: String get() = SmartSessionEnableDigest.SMART_SESSION_ADDRESS

    /** `approve(address,uint256)` — the spend cap (SpendingLimits) sits on the TOKEN's approve action (KAN-150). */
    const val APPROVE_SELECTOR: String = "0x095ea7b3"

    /** `OwnableValidator` initData = `abi.encode(uint256 threshold, address[] owners)`, single owner / threshold 1. */
    fun ownableInitData(owner: String): String {
        val words = word(Quantity.of(1).toBytes32()) +    // threshold = 1
            word(Quantity.of(0x40).toBytes32()) +          // offset to the address[] (head is 2 words)
            word(Quantity.of(1).toBytes32()) +             // owners.length = 1
            word(addr32(owner))                            // owners[0]
        return "0x" + Hex.encode(words)
    }

    /** spending-limit initData = `abi.encode(address[] tokens, uint256[] limits)` — single (token, cap) pair
     *  (the real Rhinestone `SpendingLimitsPolicy` shape; KAN-150). */
    fun spendingLimitInitData(token: String, capBaseUnits: String): String {
        val words = word(Quantity.of(0x40).toBytes32()) +  // offset → tokens[]
            word(Quantity.of(0x80).toBytes32()) +           // offset → limits[]
            word(Quantity.of(1).toBytes32()) +              // tokens.length = 1
            word(addr32(token)) +                           // tokens[0]
            word(Quantity.of(1).toBytes32()) +              // limits.length = 1
            word(decimalTo32(capBaseUnits))                 // limits[0] = cap
        return "0x" + Hex.encode(words)
    }

    /** time-frame initData = `abi.encodePacked(uint48 validUntil, uint48 validAfter)` — the real Rhinestone
     *  `TimeFramePolicy` shape (12 bytes; KAN-150), NOT two 32-byte words. */
    fun timeFrameInitData(validAfter: Long, validUntil: Long): String =
        "0x" + Hex.encode(uint48(validUntil) + uint48(validAfter))

    /** `permissionId = keccak256(abi.encode(address sessionValidator, bytes initData, bytes32 salt))`. */
    fun permissionId(sessionValidator: String, sessionValidatorInitData: String, salt: String): String {
        val initBytes = bytes(sessionValidatorInitData)
        // abi.encode(address, bytes, bytes32): head = [validator, offset=0x60, salt]; tail = [len, data(padded)].
        val head = addr32(sessionValidator) + word(Quantity.of(0x60).toBytes32()) + word(salt32(salt))
        val tail = Quantity.of(initBytes.size.toLong()).toBytes32() + pad32(initBytes)
        return "0x" + Hex.encode(Keccak.keccak256(head + tail))
    }

    /**
     * The signed-permissions for a DCA session (KAN-150, C3-corrected placement, on-chain-proven): the spend
     * CAP sits on the [spendToken]'s `approve` action — the `SpendingLimitsPolicy` parses the ERC-20 approve
     * on the token (it is rejected in `userOpPolicies`); the TimeFrame **window** is the `userOpPolicy` and is
     * repeated on the swap action; the DEX swap on [swapTarget] is a **separate** action.
     */
    fun permissions(
        spendToken: String,
        capBaseUnits: String,
        validAfter: Long,
        validUntil: Long,
        swapTarget: String,
        swapSelector: String,
    ): SignedPermissions {
        val timeFrame = PolicyData(TIMEFRAME_POLICY, timeFrameInitData(validAfter, validUntil))
        return SignedPermissions(
            permitERC4337Paymaster = true, // DCA uses Pimlico sponsoring (part of the signed hash)
            userOpPolicies = listOf(timeFrame), // TimeFrame WINDOW (IUserOpPolicy)
            actions = listOf(
                // cap: SpendingLimits on the spend-token approve (IActionPolicy) — caps what the router can pull
                ActionData(APPROVE_SELECTOR, spendToken, listOf(PolicyData(SPENDING_LIMIT_POLICY, spendingLimitInitData(spendToken, capBaseUnits)))),
                // the DEX swap — a separate, window-bounded action
                ActionData(swapSelector, swapTarget, listOf(timeFrame)),
            ),
        )
    }

    /**
     * The complete on-device enable for [config] (single DCA action) as [owner], with the app-chosen [salt],
     * the RPC-read [nonce], and the window start [windowStart] (validAfter; validUntil = the action's expiry).
     * Throws if the config isn't the single-spending-limit single-action DCA shape.
     */
    fun build(config: SessionConfig, owner: String, salt: String, nonce: Long, windowStart: Long): BuiltEnable {
        val action = config.actions.singleOrNull() ?: error("DCA enable expects exactly one action")
        val limit = action.spendingLimits.singleOrNull() ?: error("DCA enable expects exactly one spending limit")
        val initData = ownableInitData(owner)
        val permissions = permissions(
            spendToken = limit.token,
            capBaseUnits = limit.cap,
            validAfter = windowStart,
            validUntil = action.validUntil,
            swapTarget = action.target,
            swapSelector = action.selector,
        )
        val digest = "0x" + Hex.encode(
            SmartSessionEnableDigest.enableDigest(
                account = config.account,
                chainId = config.chainId,
                sessionValidator = OWNABLE_VALIDATOR,
                sessionValidatorInitData = initData,
                salt = salt,
                nonce = nonce.toString(),
                permissions = permissions,
            ),
        )
        return BuiltEnable(
            digestToSign = digest,
            account = config.account,
            chainId = config.chainId,
            sessionValidator = OWNABLE_VALIDATOR,
            sessionValidatorInitData = initData,
            salt = salt,
            nonce = nonce.toString(),
            permissionId = permissionId(OWNABLE_VALIDATOR, initData, salt),
            permissions = permissions,
        )
    }

    /** Calldata for the read-only `getNonce(permissionId, account)` eth_call (selector ++ 2 static words). */
    fun nonceCalldata(permissionId: String, account: String): ByteArray =
        bytes(GET_NONCE_SELECTOR) + salt32(permissionId) + addr32(account)

    /**
     * Decode a `getNonce` eth_call return — **fail-closed** (N1). A valid uint256 is exactly 32 bytes; an
     * empty `0x` / short return means the call never reached a real `getNonce` (wrong selector, no code at
     * [SMART_SESSION_ADDRESS], or an empty node) and MUST reject — never silently fall to nonce 0 (a real
     * first-session nonce 0 returns 32 zero-bytes, which is accepted). A wrong nonce only DoS-es the grant
     * (on-chain reject), but a silent 0 would hide a misconfig.
     */
    fun decodeNonce(returnData: ByteArray): Long {
        if (returnData.size != 32) {
            throw IllegalStateException("getNonce returned ${returnData.size} bytes (expected 32) — SmartSession module unreachable / wrong selector")
        }
        return Quantity.ofBytes(returnData).toLong()
    }

    // ── ABI word helpers ──
    // ABI addresses are raw 20-byte words (no EIP-55 checksum gate — abi.encode is case-insensitive bytes).
    private fun addr32(hex: String): ByteArray {
        val b = bytes(hex)
        require(b.size == 20) { "address must be 20 bytes: $hex" }
        return pad32Left(b)
    }
    private fun salt32(hex: String): ByteArray { val b = bytes(hex); require(b.size == 32) { "expected 32 bytes" }; return b }
    private fun word(b: ByteArray): ByteArray { require(b.size == 32); return b }
    private fun bytes(hex: String): ByteArray = Hex.decodeOrNull(hex.removePrefix("0x").removePrefix("0X")) ?: error("bad hex")
    private fun pad32Left(b: ByteArray): ByteArray { val out = ByteArray(32); b.copyInto(out, 32 - b.size); return out }
    /** 6-byte big-endian uint48 (for the packed TimeFrame initData). */
    private fun uint48(v: Long): ByteArray {
        require(v in 0..0xFFFFFFFFFFFFL) { "uint48 out of range: $v" }
        val out = ByteArray(6); var x = v
        for (i in 5 downTo 0) { out[i] = (x and 0xFF).toByte(); x = x ushr 8 }
        return out
    }
    private fun pad32(b: ByteArray): ByteArray { val n = ((b.size + 31) / 32) * 32; val out = ByteArray(n); b.copyInto(out); return out }

    /** Decimal (base-10) string → 32-byte big-endian uint256, float-free; throws on overflow/non-digit. */
    private fun decimalTo32(dec: String): ByteArray {
        val out = ByteArray(32)
        for (ch in dec) {
            val d = ch - '0'
            require(d in 0..9) { "non-decimal in amount: $dec" }
            var carry = d
            for (i in 31 downTo 0) {
                val v = (out[i].toInt() and 0xFF) * 10 + carry
                out[i] = (v and 0xFF).toByte()
                carry = v ushr 8
            }
            require(carry == 0) { "amount overflows uint256: $dec" }
        }
        return out
    }
}
