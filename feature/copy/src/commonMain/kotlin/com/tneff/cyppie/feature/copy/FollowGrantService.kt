package com.tneff.cyppie.feature.copy

import com.tneff.cyppie.evm.VerifiedGrant
import com.tneff.cyppie.wallet.SeedSource

/**
 * The `/prepare` result the Confirm screen renders (KAN-155, Dev-2 KAN-154 contract). It deliberately
 * splits two trust levels:
 *  - [verifiedGrant] — the **cryptographically verified** authorization, decoded from the bytes inside the
 *    signed enable digest (`verifyGrant`): account / chain / router (`actionTarget`) / `actionSelector` /
 *    `spendToken` / `capBaseUnits` (cumulative total budget) / window. The Permit2 leg is infra (pinned +
 *    checked by `verifyGrant`) but is NOT part of this struct — not rendered as an action.
 *  - [source] (the followed trader) + [allocationBps] — **advisory** scope/mirror-time metadata; NOT in the
 *    signed enable, so NOT crypto-guaranteed. The UI shows them clearly separated, never as the guarantee.
 */
data class CopyGrantPreview(
    val verifiedGrant: VerifiedGrant,
    val source: String,
    val allocationBps: Int,
)

/**
 * The seam to Dev-2's copy grant/crypto (KAN-154). [prepare] builds the Follow-Trader Smart-Session enable
 * for [trader] + [budgetBaseUnits] **on-device** and returns the verified [CopyGrantPreview] — **fail-closed**:
 * it throws on any build/verify mismatch and the caller must NOT proceed to sign. [activate] runs re-auth →
 * verifyEnableUserOp → owner-sign → broadcast with the [seedSource] (zeroized after).
 */
interface FollowGrantService {
    suspend fun prepare(trader: String, budgetBaseUnits: String): CopyGrantPreview
    suspend fun activate(preview: CopyGrantPreview, seedSource: SeedSource)
}

/**
 * Stub [FollowGrantService] for building the UI before Dev-2's KAN-154 impl lands — returns a shape-correct
 * preview (cap echoes the entered budget; 100% allocation) so the flow + 2-section disclosure + Maestro run
 * end-to-end. It still zeroizes the seed in [activate] (no real sign). NOT for production (the app shell
 * must not wire this in release).
 */
class StubFollowGrantService(private val nowEpochSeconds: () -> Long) : FollowGrantService {
    override suspend fun prepare(trader: String, budgetBaseUnits: String): CopyGrantPreview {
        val now = nowEpochSeconds()
        return CopyGrantPreview(
            verifiedGrant = VerifiedGrant(
                account = "0x0000000000000000000000000000000000000000",
                chainId = 1L,
                actionTarget = COPY_SERVICE_ROUTER, // the UniversalRouter the grant authorizes
                actionSelector = EXECUTE_SELECTOR,
                spendToken = USDC,
                capBaseUnits = budgetBaseUnits,
                windowStartEpochSeconds = now,
                windowEndEpochSeconds = now + 90L * 86_400L,
            ),
            source = trader,
            allocationBps = 10_000, // 100% (v1 fixed)
        )
    }

    override suspend fun activate(preview: CopyGrantPreview, seedSource: SeedSource) {
        (seedSource as? AutoCloseable)?.close() // stub: no real sign, but still zeroize the seed
    }

    private companion object {
        const val COPY_SERVICE_ROUTER = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45" // UniversalRouter (example)
        const val EXECUTE_SELECTOR = "0x3593564c" // execute(bytes,bytes[],uint256)
        const val USDC = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    }
}
