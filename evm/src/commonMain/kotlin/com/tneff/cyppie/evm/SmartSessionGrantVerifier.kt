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
    val actionTarget: String,             // the swap target (EIP-55), e.g. the DEX router
    val actionSelector: String,           // the swap 4-byte selector (0x........)
    val spendToken: String,               // EIP-55 token the spending-limit caps
    val capBaseUnits: String,             // cumulative cap (total budget), base-unit decimal String
    val windowStartEpochSeconds: Long,    // time-frame validAfter
    val windowEndEpochSeconds: Long,      // time-frame validUntil
)

/**
 * On-device **grant verification** (PRD-05 Ph1, approach C / V1+decode; KAN-144, C3-corrected placement KAN-150).
 * Closes the cap-blind P0-Δ: the [SmartSessionEnableDigest] proves the session *structure* is in the signed
 * hash, and this layer additionally **decodes** the spending-limit + time-frame policy `initData` and **pins the
 * policy/validator addresses** so the cap/token/window the user sees are derived from — and equal to — the
 * signed bytes (decode can't lie and can't break an honest grant, unlike re-encoding).
 *
 * Cyppie Ph1 DCA grant shape (fail-closed if it differs; KAN-150 — the on-chain-valid placement):
 *  - `userOpPolicies` = exactly **one** TimeFrame window policy;
 *  - `actions` = exactly **two**: the spend cap sits on the **token-`approve` action** (`SpendingLimits`, because
 *    the policy parses the ERC-20 approve/transfer at the action target), and the **swap action** (the DEX
 *    router call) is time-boxed by the TimeFrame policy;
 *  - broad-access flags (`permitAdminAccess`/`permitGenericPolicy`/`ignoreSecurityAttestations`) MUST be false.
 *
 * 🔒 [SPENDING_LIMIT_POLICY]/[TIMEFRAME_POLICY]/[SESSION_VALIDATOR]/`smartSession` are **client-pinned constants**,
 * never from the backend. The caller passes [account] = the device-derived owner EOA (never a backend address).
 */
object SmartSessionGrantVerifier {

    // Deployed Rhinestone GLOBAL_CONSTANTS policy addresses — CREATE2-deterministic, identical on ETH + Base
    // (on-chain verified). These are the addresses actually emitted into `PolicyData.policy` — NOT the SDK's
    // legacy `constants.js` addresses (pinning those would reject every real session). Compared case-insensitively.
    const val SPENDING_LIMIT_POLICY: String = "0x000000000033212e272655d8a22402db819477a6"
    const val TIMEFRAME_POLICY: String = "0x0000000000D30f611fA3bf652ac6879428586930"

    // Deployed Rhinestone OwnableValidator (GLOBAL_CONSTANTS) — emitted into `SignedSession.sessionValidator`,
    // CREATE2-uniform ETH+Base. NOT the legacy top-level `OWNABLE_VALIDATOR_ADDRESS` (0x2483DA…) export.
    const val SESSION_VALIDATOR: String = "0x000000000013fdB5234E4E3162a810F54d9f7E98"

    /** ERC-20 `approve(address,uint256)` — the action selector the spending-limit cap is attached to (KAN-150). */
    const val APPROVE_SELECTOR: String = "0x095ea7b3"

    /**
     * Verifies [digestToSign] against the disclosed grant material and returns the [VerifiedGrant] to render.
     * Throws [GrantVerificationException] on ANY mismatch (digest, pinned address, shape, broad flags) — the
     * caller must NOT sign on throw.
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
        sessionValidatorPin: String = SESSION_VALIDATOR,
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

        // 2. The session validator must be the pinned OwnableValidator (catches the legacy-validator gotcha).
        if (!sessionValidator.equals(sessionValidatorPin, ignoreCase = true)) {
            throw GrantVerificationException("unexpected session validator $sessionValidator")
        }

        // 3. Broad-access flags must be off (they would grant scope beyond the displayed action).
        if (permissions.permitAdminAccess || permissions.permitGenericPolicy || permissions.ignoreSecurityAttestations) {
            throw GrantVerificationException("grant requests broad access (admin/generic/ignore-attestations)")
        }

        // 4. userOpPolicies: exactly one TimeFrame window at the pinned policy → decode (start, end).
        val windowPolicy = permissions.userOpPolicies.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one userOp (time-frame) policy")
        if (!windowPolicy.policy.equals(timeFramePolicy, ignoreCase = true)) {
            throw GrantVerificationException("unexpected time-frame policy address ${windowPolicy.policy}")
        }
        val (start, end) = decodeTimeFrame(windowPolicy.initData)

        // 5. Exactly two actions: the spend cap (token approve) + the time-boxed swap (KAN-150 placement).
        if (permissions.actions.size != 2) {
            throw GrantVerificationException("expected exactly two actions (approve + swap), was ${permissions.actions.size}")
        }
        val approve = permissions.actions.singleOrNull { it.actionTargetSelector.equals(APPROVE_SELECTOR, ignoreCase = true) }
            ?: throw GrantVerificationException("expected exactly one token-approve action carrying the cap")
        val swap = permissions.actions.first { it !== approve }

        // 5a. The approve action carries the spending-limit (cap) at the pinned policy → decode (token, cap).
        val capPolicy = approve.actionPolicies.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one spending-limit policy on the approve action")
        if (!capPolicy.policy.equals(spendingLimitPolicy, ignoreCase = true)) {
            throw GrantVerificationException("unexpected spending-limit policy address ${capPolicy.policy}")
        }
        val (token, cap) = decodeSpendingLimit(capPolicy.initData)
        // The cap is enforced on the ERC-20 the approve targets — they must be the same token.
        if (!approve.actionTarget.equals(token, ignoreCase = true)) {
            throw GrantVerificationException("spending-limit token $token != approve target ${approve.actionTarget}")
        }

        // 5b. The swap action is time-boxed at the pinned time-frame policy; its window must match the userOp window.
        val swapTime = swap.actionPolicies.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one time-frame policy on the swap action")
        if (!swapTime.policy.equals(timeFramePolicy, ignoreCase = true)) {
            throw GrantVerificationException("unexpected time-frame policy address on swap ${swapTime.policy}")
        }
        val (swapStart, swapEnd) = decodeTimeFrame(swapTime.initData)
        if (swapStart != start || swapEnd != end) {
            throw GrantVerificationException("swap time-frame window does not match the userOp window")
        }

        // 6. The swap is the user-facing action (router + selector); the cap/token come from the approve action.
        return VerifiedGrant(
            account = account,
            chainId = chainId,
            actionTarget = swap.actionTarget,
            actionSelector = swap.actionTargetSelector,
            spendToken = token,
            capBaseUnits = cap,
            windowStartEpochSeconds = start,
            windowEndEpochSeconds = end,
        )
    }

    /**
     * spending-limit `initData` = `abi.encode(address[] tokens, uint256[] limits)` — the audited
     * `getSpendingLimitsPolicy` output. For the single-token DCA cap this is 6 static words
     * (offset 0x40, offset 0x80, len 1, token, len 1, limit). Fail-closed if it isn't that exact shape.
     */
    internal fun decodeSpendingLimit(initDataHex: String): Pair<String, String> {
        val bytes = decodeBytes(initDataHex, "spending-limit")
        if (bytes.size != 192) {
            throw GrantVerificationException("spending-limit initData must be 192 bytes (single-token), was ${bytes.size}")
        }
        fun word(i: Int) = bytes.copyOfRange(i * 32, i * 32 + 32)
        if (wordToLong(word(0)) != 0x40L) throw GrantVerificationException("unexpected tokens offset")
        if (wordToLong(word(1)) != 0x80L) throw GrantVerificationException("unexpected limits offset")
        if (wordToLong(word(2)) != 1L) throw GrantVerificationException("expected exactly one spend token")
        if (wordToLong(word(4)) != 1L) throw GrantVerificationException("expected exactly one spend limit")
        val tokenWord = word(3)
        for (i in 0 until 12) if (tokenWord[i].toInt() != 0) throw GrantVerificationException("malformed token padding")
        val token = EvmAddress.fromBytes(tokenWord.copyOfRange(12, 32)).value
        val cap = bytes32ToDecimal(word(5))
        return token to cap
    }

    /**
     * time-frame `initData` = `encodePacked(uint48 validUntil, uint48 validAfter)` — the audited
     * `getTimeFramePolicy` output (12 bytes; **validUntil first**). Returns `(start=validAfter, end=validUntil)`.
     */
    internal fun decodeTimeFrame(initDataHex: String): Pair<Long, Long> {
        val bytes = decodeBytes(initDataHex, "time-frame")
        if (bytes.size != 12) {
            throw GrantVerificationException("time-frame initData must be 12 bytes (packed uint48 pair), was ${bytes.size}")
        }
        val validUntil = Quantity.ofBytes(bytes.copyOfRange(0, 6)).toLong()
        val validAfter = Quantity.ofBytes(bytes.copyOfRange(6, 12)).toLong()
        return validAfter to validUntil // (start, end)
    }

    private fun decodeBytes(hex: String, label: String): ByteArray =
        Hex.decodeOrNull(hex.removePrefix("0x").removePrefix("0X"))
            ?: throw GrantVerificationException("invalid $label initData hex")

    private fun wordToLong(w: ByteArray): Long = Quantity.ofBytes(w).toLong()

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
