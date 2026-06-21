package com.tneff.cyppie.feature.copy

import com.tneff.cyppie.wallet.SeedSource

/** One display leg of the verified copy grant — a `(router, allowed-function)` the grant authorizes. The
 *  production grant has N legs (router.execute = display-target); infra legs (e.g. Permit2) are filtered
 *  out by the service before display. */
data class CopyLeg(val router: String, val allowedFunction: String)

/**
 * The on-device-**verified** copy grant the disclosure renders (KAN-155). Every field is decoded from the
 * bytes inside the signed enable digest (KAN-154 `verifyGrant`), NEVER backend text. [trader] is the
 * followed address (user input, app context). [legs] is N display-legs (1 in v1; production = N).
 */
data class PreparedFollow(
    val trader: String,
    val capBaseUnits: String,
    val legs: List<CopyLeg>,
) {
    /** The primary router for the one-line disclosure summary (copy_disclosure %2$s). */
    val primaryRouter: String get() = legs.firstOrNull()?.router.orEmpty()
}

/**
 * The seam to Dev-2's copy grant/crypto (KAN-154, generalized to "1 cap + N window-actions"). [prepare]
 * builds the Follow-Trader Smart-Session enable for [trader] + [budgetBaseUnits] **on-device** and returns
 * the VERIFIED grant — **fail-closed**: it throws on any build/verify mismatch and the caller must NOT sign.
 * [activate] signs the prepared grant on-device with the re-auth [seedSource] (zeroized after) + registers it.
 */
interface FollowGrantService {
    suspend fun prepare(trader: String, budgetBaseUnits: String): PreparedFollow
    suspend fun activate(prepared: PreparedFollow, seedSource: SeedSource)
}

/**
 * Stub [FollowGrantService] for building the UI before Dev-2's KAN-154 lands — returns a shape-correct
 * single-leg grant (cap echoes the entered budget) so the flow + disclosure + Maestro run end-to-end. It
 * still zeroizes the seed in [activate] (no real sign). NOT for production (the app shell must not wire this
 * in release).
 */
class StubFollowGrantService : FollowGrantService {
    override suspend fun prepare(trader: String, budgetBaseUnits: String): PreparedFollow =
        PreparedFollow(
            trader = trader,
            capBaseUnits = budgetBaseUnits,
            legs = listOf(CopyLeg(router = COPY_SERVICE_ROUTER, allowedFunction = EXECUTE_SELECTOR)),
        )

    override suspend fun activate(prepared: PreparedFollow, seedSource: SeedSource) {
        (seedSource as? AutoCloseable)?.close() // stub: no real sign, but still zeroize the seed
    }

    private companion object {
        const val COPY_SERVICE_ROUTER = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45" // UniversalRouter (example)
        const val EXECUTE_SELECTOR = "0x3593564c" // execute(bytes,bytes[],uint256)
    }
}
