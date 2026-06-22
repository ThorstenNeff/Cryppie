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

/** One per-token SELL-cap inside a [VerifiedBasketGrant] (the SpendingLimit on that token's approve). */
data class TokenCap(val token: String, val capBaseUnits: String)

/**
 * The **verified** Strategy/basket grant (PRD-07b, KAN-165) — the M-cap analogue of [VerifiedGrant]. [caps] are the
 * on-chain-pinned SELL-caps over the user's basket(+budget) tokens (🔒); [actionTarget] is the swap router. The
 * swap `tokenOut` (buy direction) and the target weights are NOT here — they are advisory (ℹ️), bounded only by
 * the per-token sell-caps + the off-chain allowlist/slippage. Every field is derived from the signed enable bytes.
 */
data class VerifiedBasketGrant(
    val account: String,
    val chainId: Long,
    val actionTarget: String,             // the swap router (EIP-55), e.g. the UniversalRouter
    val actionSelector: String,           // the swap 4-byte selector
    val caps: List<TokenCap>,             // M per-token sell-caps (the basket + budget tokens), in action order
    val windowStartEpochSeconds: Long,
    val windowEndEpochSeconds: Long,
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

    // ── Copy-trading (KAN-154) infra pins. Source = official Uniswap deployment docs (the trusted client anchor;
    // the backend `actions` are checked AGAINST these, never the reverse). Permit2 is chain-uniform; the
    // UniversalRouter is per-chain. The backend session-key's enabled action set = these targets/selectors. ──
    /** Uniswap Permit2 — canonical CREATE2 address, identical on every chain (doc-confirmed). */
    const val PERMIT2: String = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
    /** Permit2 `approve(address,address,uint160,uint48)`. */
    const val PERMIT2_APPROVE_SELECTOR: String = "0x87517c45"
    /** UniversalRouter `execute(bytes,bytes[],uint256)`. */
    const val UNIVERSAL_ROUTER_EXECUTE_SELECTOR: String = "0x3593564c"

    /** The Uniswap UniversalRouter (V4) per supported chain; null if the chain is unsupported. */
    fun universalRouter(chainId: Long): String? = when (chainId) {
        1L -> "0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af" // Ethereum (doc-confirmed)
        8453L -> "0x6fF5693b99212Da76ad316178A184AB56D299b43" // Base (confirmed by the Copy-KAT digest reconcile)
        else -> null
    }

    /** A pinned action: the contract [target] + the 4-byte [selector] the session is allowed to call. */
    data class ActionPin(val target: String, val selector: String)

    /**
     * Verifies [digestToSign] against the disclosed grant material and returns the [VerifiedGrant] to render.
     * Throws [GrantVerificationException] on ANY mismatch — the caller must NOT sign on throw.
     *
     * Generalized (KAN-154) to **"1 cap-action + 1 swap-action + N pinned infra-actions"**: every disclosed action
     * MUST classify as the cap (token `approve` → SpendingLimits), the primary swap ([swapTarget]/[swapSelector]),
     * or one of the pinned [infraActions] — an unknown target/selector or any extra action is **fail-closed**
     * (stops a rogue backend smuggling an N+1th action on a malicious target). DCA = no infra ([infraActions] empty,
     * 2 actions); Copy-UniversalRouter = [infraActions] = the Permit2 approve (3 actions).
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
        swapTarget: String,
        swapSelector: String,
        infraActions: List<ActionPin> = emptyList(),
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

        // 5. Classify EVERY action: exactly one cap (token approve → SpendingLimits), one swap (the pinned primary),
        //    and one each of the pinned infra-actions. An unknown target/selector or any extra/duplicate action is
        //    fail-closed (stops a rogue backend smuggling an N+1th action on a malicious target).
        var capToken: String? = null
        var capAmount: String? = null
        var sawSwap = false
        val seenInfra = HashSet<Int>()
        for (action in permissions.actions) {
            when {
                action.actionTargetSelector.equals(APPROVE_SELECTOR, ignoreCase = true) -> {
                    if (capToken != null) throw GrantVerificationException("more than one cap (approve) action")
                    val capPolicy = action.actionPolicies.singleOrNull()
                        ?: throw GrantVerificationException("expected exactly one spending-limit policy on the approve action")
                    if (!capPolicy.policy.equals(spendingLimitPolicy, ignoreCase = true)) {
                        throw GrantVerificationException("unexpected spending-limit policy address ${capPolicy.policy}")
                    }
                    val (token, cap) = decodeSpendingLimit(capPolicy.initData)
                    // The cap is enforced on the ERC-20 the approve targets — they must be the same token.
                    if (!action.actionTarget.equals(token, ignoreCase = true)) {
                        throw GrantVerificationException("spending-limit token $token != approve target ${action.actionTarget}")
                    }
                    capToken = token
                    capAmount = cap
                }
                action.actionTarget.equals(swapTarget, ignoreCase = true) &&
                    action.actionTargetSelector.equals(swapSelector, ignoreCase = true) -> {
                    if (sawSwap) throw GrantVerificationException("more than one swap action")
                    requireWindowAction(action, timeFramePolicy, start, end)
                    sawSwap = true
                }
                else -> {
                    val idx = infraActions.indexOfFirst {
                        action.actionTarget.equals(it.target, ignoreCase = true) &&
                            action.actionTargetSelector.equals(it.selector, ignoreCase = true)
                    }
                    if (idx < 0) {
                        throw GrantVerificationException("unknown action ${action.actionTargetSelector}@${action.actionTarget}")
                    }
                    if (!seenInfra.add(idx)) throw GrantVerificationException("duplicate infra action")
                    requireWindowAction(action, timeFramePolicy, start, end)
                }
            }
        }
        if (capToken == null || capAmount == null) throw GrantVerificationException("missing cap (token approve) action")
        if (!sawSwap) throw GrantVerificationException("missing swap action $swapSelector@$swapTarget")
        if (seenInfra.size != infraActions.size) throw GrantVerificationException("missing pinned infra action(s)")

        // 6. The swap is the user-facing action (router + selector); the cap/token come from the approve action.
        return VerifiedGrant(
            account = account,
            chainId = chainId,
            actionTarget = swapTarget,
            actionSelector = swapSelector,
            spendToken = capToken,
            capBaseUnits = capAmount,
            windowStartEpochSeconds = start,
            windowEndEpochSeconds = end,
        )
    }

    /**
     * Strategy/basket grant (PRD-07b, KAN-165) — the **M-cap** generalization of [verifyGrant]. Instead of exactly
     * one cap, the session caps the SELL side of EACH of the user's [expectedCapTokens] (basket + budget). Shape:
     * `userOpPolicies = [TimeFrame]`; `actions = M×(token.approve → SpendingLimits) + 1×(swap → TimeFrame) +
     * N×(infra → TimeFrame)`. Each cap-action is pinned to a distinct token in [expectedCapTokens] (no unknown / no
     * duplicate / none missing); the swap + infra are window-policed like [verifyGrant]. Fail-closed on any deviation.
     * The cap tokens are the on-chain SELL set (🔒); the swap `tokenOut`/weights are advisory (not pinned here).
     *
     * @throws GrantVerificationException on ANY mismatch — the caller must NOT sign on throw.
     */
    fun verifyBasketGrant(
        account: String,
        chainId: Long,
        sessionValidator: String,
        sessionValidatorInitData: String,
        salt: String,
        nonce: String,
        permissions: SignedPermissions,
        digestToSign: String,
        swapTarget: String,
        swapSelector: String,
        expectedCaps: Map<String, String>,
        infraActions: List<ActionPin> = emptyList(),
        spendingLimitPolicy: String = SPENDING_LIMIT_POLICY,
        timeFramePolicy: String = TIMEFRAME_POLICY,
        sessionValidatorPin: String = SESSION_VALIDATOR,
        smartSession: String = SmartSessionEnableDigest.SMART_SESSION_ADDRESS,
    ): VerifiedBasketGrant {
        if (expectedCaps.isEmpty()) throw GrantVerificationException("basket grant needs at least one cap token")
        // 1-3: identical envelope checks to verifyGrant (digest binds the disclosed material to the pinned module).
        val recomputed = Hex.encode(
            SmartSessionEnableDigest.enableDigest(account, chainId, sessionValidator, sessionValidatorInitData, salt, nonce, permissions, smartSession),
        )
        if (!recomputed.equals(normalizeDigest(digestToSign), ignoreCase = true)) {
            throw GrantVerificationException("enable digest mismatch — refusing to sign")
        }
        if (!sessionValidator.equals(sessionValidatorPin, ignoreCase = true)) {
            throw GrantVerificationException("unexpected session validator $sessionValidator")
        }
        if (permissions.permitAdminAccess || permissions.permitGenericPolicy || permissions.ignoreSecurityAttestations) {
            throw GrantVerificationException("grant requests broad access (admin/generic/ignore-attestations)")
        }
        // 4: exactly one TimeFrame userOp window.
        val windowPolicy = permissions.userOpPolicies.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one userOp (time-frame) policy")
        if (!windowPolicy.policy.equals(timeFramePolicy, ignoreCase = true)) {
            throw GrantVerificationException("unexpected time-frame policy address ${windowPolicy.policy}")
        }
        val (start, end) = decodeTimeFrame(windowPolicy.initData)

        // 5: classify every action — M caps (each on a distinct expected token AT the granted cap value), one swap,
        //    the pinned infra. 🔒 the permissionId does NOT bind caps/router/window — the SCOPE lives entirely in
        //    these actions, so the cap VALUE (not just the token) MUST be checked against the granted per-token cap.
        val expected = expectedCaps.entries.associate { it.key.lowercase() to it.value }
        val seenCaps = HashSet<String>()
        val caps = ArrayList<TokenCap>()
        var sawSwap = false
        val seenInfra = HashSet<Int>()
        for (action in permissions.actions) {
            when {
                action.actionTargetSelector.equals(APPROVE_SELECTOR, ignoreCase = true) -> {
                    val capPolicy = action.actionPolicies.singleOrNull()
                        ?: throw GrantVerificationException("expected exactly one spending-limit policy on the approve action")
                    if (!capPolicy.policy.equals(spendingLimitPolicy, ignoreCase = true)) {
                        throw GrantVerificationException("unexpected spending-limit policy address ${capPolicy.policy}")
                    }
                    val (token, cap) = decodeSpendingLimit(capPolicy.initData)
                    if (!action.actionTarget.equals(token, ignoreCase = true)) {
                        throw GrantVerificationException("spending-limit token $token != approve target ${action.actionTarget}")
                    }
                    val key = token.lowercase()
                    val wantCap = expected[key]
                        ?: throw GrantVerificationException("cap on unexpected token $token (not in the basket+budget set)")
                    if (cap != wantCap) throw GrantVerificationException("cap value for $token: signed $cap != granted $wantCap")
                    if (!seenCaps.add(key)) throw GrantVerificationException("duplicate cap for token $token")
                    caps.add(TokenCap(token, cap))
                }
                action.actionTarget.equals(swapTarget, ignoreCase = true) &&
                    action.actionTargetSelector.equals(swapSelector, ignoreCase = true) -> {
                    if (sawSwap) throw GrantVerificationException("more than one swap action")
                    requireWindowAction(action, timeFramePolicy, start, end)
                    sawSwap = true
                }
                else -> {
                    val idx = infraActions.indexOfFirst {
                        action.actionTarget.equals(it.target, ignoreCase = true) &&
                            action.actionTargetSelector.equals(it.selector, ignoreCase = true)
                    }
                    if (idx < 0) throw GrantVerificationException("unknown action ${action.actionTargetSelector}@${action.actionTarget}")
                    if (!seenInfra.add(idx)) throw GrantVerificationException("duplicate infra action")
                    requireWindowAction(action, timeFramePolicy, start, end)
                }
            }
        }
        if (seenCaps.size != expected.size) {
            throw GrantVerificationException("missing cap(s): expected ${expected.size} basket+budget tokens, got ${seenCaps.size}")
        }
        if (!sawSwap) throw GrantVerificationException("missing swap action $swapSelector@$swapTarget")
        if (seenInfra.size != infraActions.size) throw GrantVerificationException("missing pinned infra action(s)")

        return VerifiedBasketGrant(account, chainId, swapTarget, swapSelector, caps, start, end)
    }

    /** A window-policed action: exactly one TimeFrame policy at the pinned address, window == the userOp window. */
    private fun requireWindowAction(action: SmartSessionEnableDigest.ActionData, timeFramePolicy: String, start: Long, end: Long) {
        val tp = action.actionPolicies.singleOrNull()
            ?: throw GrantVerificationException("expected exactly one time-frame policy on action ${action.actionTargetSelector}")
        if (!tp.policy.equals(timeFramePolicy, ignoreCase = true)) {
            throw GrantVerificationException("unexpected time-frame policy on action ${tp.policy}")
        }
        val (s, e) = decodeTimeFrame(tp.initData)
        if (s != start || e != end) throw GrantVerificationException("action time-frame window != userOp window")
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
