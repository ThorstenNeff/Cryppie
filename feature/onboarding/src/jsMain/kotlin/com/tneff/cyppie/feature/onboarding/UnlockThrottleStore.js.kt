package com.tneff.cyppie.feature.onboarding

// Web has no local wallet → no unlock throttle to persist.
actual object UnlockThrottleStore {
    actual suspend fun load(): ThrottleSnapshot = ThrottleSnapshot(0, Long.MAX_VALUE)
    actual suspend fun recordFailure(): Int = 0
    actual suspend fun clear() {}
}
