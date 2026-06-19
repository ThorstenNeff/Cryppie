package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions

/** Fail-closed signal: the disclosed grant did not verify against the digest / pinned policies (KAN-144). */
class GrantVerificationException(message: String) : RuntimeException(message)

/**
 * The **verified** Smart-Session grant — every field is derived from the bytes that are actually inside the
 * signed enable digest (KAN-144, approach C / V1+decode). The grant UI renders **only** this (never the raw
 * backend material), so what the user sees is provably what they sign.
 */
data class VerifiedGrant(
    val account: String,                  // EIP-55 SCA/owner (7702 same-address)
    val chainId: Long,
    val actionTarget: String,             // the single allowed contract (EIP-55), e.g. the DEX router
    val actionSelector: String,           // the single allowed 4-byte selector (0x........)
    val spendToken: String,               // EIP-55 token the spending-limit caps
    val capBaseUnits: String,             // spending cap, base-unit decimal String (format with token decimals in UI)
    val windowStartEpochSeconds: Long,    // time-frame policy start
    val windowEndEpochSeconds: Long,      // time-frame policy end
)

/**
 * On-device **grant verification** (PRD-05 Ph1, approach C / V1+decode, KAN-144). Closes the cap-blind P0-Δ:
 * the [SmartSessionEnableDigest] proves the session *structure* (scope/selector/target/validator/account/chain
 * + policy addresses + initData are all in the signed hash), and this layer additionally **decodes** the
 * spending-limit + time-frame policy `initData` and **pins the policy addresses** so the cap/token/window the
 * user sees are derived from — and equal to — the signed bytes (decode can't lie and can't break an honest
 * grant, unlike re-encoding).
 *
 * Cyppie Ph1 DCA grant shape (fail-closed if it differs): exactly **one** spending-limit `userOpPolicy` +
 * exactly **one** swap `action` carrying exactly **one** time-frame action-policy; the broad-access flags
 * (`permitAdminAccess` / `permitGenericPolicy` / `ignoreSecurityAttestations`) MUST be false.
 *
 * 🔒 [SPENDING_LIMIT_POLICY] / [TIMEFRAME_POLICY] / `smartSession` are **client-pinned constants**, never from
 * the backend. The caller passes [account] = the device-derived owner EOA (never a backend address).
 */
object SmartSessionGrantVerifier {

    // ⚠️ PLACEHOLDER pins from the backend reference vectors — REPLACE with the real deployed Rhinestone
    // policy addresses (ETH + Base; same address per CREATE2 expected) before production. Until then the
    // caller must pass the real addresses explicitly. Tracked as the KAN-144 backend input.
    const val SPENDING_LIMIT_POLICY: String = "0x0000000000000000000000000000000000000511"
    const val TIMEFRAME_POLICY: String = "0x0000000000000000000000000000000000000522"

    /**
     * Verifies [digestToSign] against the disclosed grant material and returns the [VerifiedGrant] to render.
     * Throws [GrantVerificationException] on ANY mismatch (digest, pinned policy address, shape, broad flags) —
     * the caller must NOT sign on throw.
     */
    fun verifyGrant(
        account: String,
        chainId: Long,
        sessionValidator: String,
        sessionValidatorInitData: String,
        salt: String,
        nonce: String,
        permissions: SignedPermissions,
        digestToSign: String,
        spendingLimitPolicy: String = SPENDING_LIMIT_POLICY,
        timeFramePolicy: String = TIMEFRAME_POLICY,
        smartSession: String = SmartSessionEnableDigest.SMART_SESSION_ADDRESS,
    ): VerifiedGrant {
        // 1. The enable digest must match what we're asked to sign (over the disclosed material + pinned module).
        val recomputed = Hex.encode(
            SmartSessionEnableDigest.enableDigest(
                account = account,
                chainId = chainId,
                sessionValidator = sessionValidator,
                sessionValidatorInitData = sessionValidatorInitData,
                salt = salt,
                nonce = nonce,
                permissions = permissions,
                smartSession = smartSession,
            ),
        )
        if (!recomputed.equals(normalizeDigest(digestToSign), ignoreCase = true)) {
            throw GrantVerificationException("enable digest mismatch — refusing to sign")
        }

        // 2. Broad-access flags must be off (they would grant scope beyond the displayed action).
        if (permissions.permitAdminAccess || permissions.permitGenericPolicy || permissions.ignoreSecurityAttestations) {
            throw GrantVerificationException("grant requests broad access (admin/generic/ignore-attestations)")
        }

        // 3. Exactly one spending-limit userOpPolicy at the pinned policy address → decode (token, cap).
        val spend = permissions.userOpPolicies.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one spending-limit policy")
        if (!spend.policy.equals(spendingLimitPolicy, ignoreCase = true)) {
            throw GrantVerificationException("unexpected spending-limit policy address ${spend.policy}")
        }
        val (token, cap) = decodeSpendingLimit(spend.initData)

        // 4. Exactly one action, with exactly one time-frame action-policy at the pinned address → decode window.
        val action = permissions.actions.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one action")
        val timePolicy = action.actionPolicies.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one action policy (time-frame)")
        if (!timePolicy.policy.equals(timeFramePolicy, ignoreCase = true)) {
            throw GrantVerificationException("unexpected time-frame policy address ${timePolicy.policy}")
        }
        val (start, end) = decodeTimeFrame(timePolicy.initData)

        return VerifiedGrant(
            account = account,
            chainId = chainId,
            actionTarget = action.actionTarget,
            actionSelector = action.actionTargetSelector,
            spendToken = token,
            capBaseUnits = cap,
            windowStartEpochSeconds = start,
            windowEndEpochSeconds = end,
        )
    }

    /** spending-limit `initData` = `abi.encode(address token, uint256 cap)` — two static 32-byte words. */
    internal fun decodeSpendingLimit(initDataHex: String): Pair<String, String> {
        val bytes = decode64(initDataHex, "spending-limit")
        for (i in 0 until 12) {
            if (bytes[i].toInt() != 0) throw GrantVerificationException("malformed spending-limit token padding")
        }
        val token = EvmAddress.fromBytes(bytes.copyOfRange(12, 32)).value
        val cap = bytes32ToDecimal(bytes.copyOfRange(32, 64))
        return token to cap
    }

    /** time-frame `initData` = `abi.encode(uint start, uint end)` — two static 32-byte words (unix seconds). */
    internal fun decodeTimeFrame(initDataHex: String): Pair<Long, Long> {
        val bytes = decode64(initDataHex, "time-frame")
        val start = Quantity.ofBytes(bytes.copyOfRange(0, 32)).toLong()
        val end = Quantity.ofBytes(bytes.copyOfRange(32, 64)).toLong()
        return start to end
    }

    private fun decode64(hex: String, label: String): ByteArray {
        val bytes = Hex.decodeOrNull(hex.removePrefix("0x").removePrefix("0X"))
            ?: throw GrantVerificationException("invalid $label initData hex")
        if (bytes.size != 64) throw GrantVerificationException("$label initData must be 64 bytes, was ${bytes.size}")
        return bytes
    }

    private fun normalizeDigest(hex: String): String = hex.removePrefix("0x").removePrefix("0X")

    /** Big-endian 32-byte unsigned → base-10 string (repeated /10). */
    private fun bytes32ToDecimal(b: ByteArray): String {
        val work = b.copyOf()
        if (work.all { it.toInt() == 0 }) return "0"
        val digits = StringBuilder()
        while (work.any { it.toInt() != 0 }) {
            var rem = 0
            for (i in work.indices) {
                val cur = (rem shl 8) or (work[i].toInt() and 0xFF)
                work[i] = (cur / 10).toByte()
                rem = cur % 10
            }
            digits.append(('0' + rem))
        }
        return digits.reverse().toString()
    }
}
