package com.tneff.cyppie.feature.onboarding

/** Persisted throttle state read at launch (KAN-92 M1). [secondsSinceLastFailure] uses the platform clock. */
data class ThrottleSnapshot(val failures: Int, val secondsSinceLastFailure: Long)

/**
 * Persists the unlock failure counter + last-failure timestamp so the exponential backoff (KAN-92)
 * **survives process death** — a force-stop + relaunch must not reset the counter and bypass the
 * lockout (M1). Backed by a small plain file next to the seed (`:storage`); the counter is non-secret.
 * Non-web (no local wallet on web → no throttle). The remaining lockout is recomputed at launch via
 * the pure [unlockRemainingLockoutSeconds] from [ThrottleSnapshot], not an ephemeral in-memory timer.
 */
expect object UnlockThrottleStore {
    /** Reads the persisted counter + how long ago the last failure was (platform clock). */
    suspend fun load(): ThrottleSnapshot

    /** Records one more failure stamped at "now"; returns the new total. */
    suspend fun recordFailure(): Int

    /** Clears the counter (successful unlock). */
    suspend fun clear()
}
