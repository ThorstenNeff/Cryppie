package com.tneff.cyppie.feature.onboarding

/** Wrong-password attempts before the first lockout kicks in (SPEC_UNLOCK §Throttle). */
const val UNLOCK_LOCKOUT_THRESHOLD: Int = 5

/**
 * Exponential backoff (seconds) applied to the 5th, 6th, 7th… consecutive wrong password: the last
 * value is the cap. No auto-wipe — KDF (210k) + backoff are the brute-force defense (ADR-0009).
 */
internal val UNLOCK_BACKOFF_SECONDS: List<Long> = listOf(30L, 60L, 300L, 900L, 1800L)

/** Lockout duration (seconds) after [failures] consecutive wrong attempts; 0 below the threshold. */
fun unlockLockoutSeconds(failures: Int): Long {
    if (failures < UNLOCK_LOCKOUT_THRESHOLD) return 0L
    val step = failures - UNLOCK_LOCKOUT_THRESHOLD
    return UNLOCK_BACKOFF_SECONDS.getOrElse(step) { UNLOCK_BACKOFF_SECONDS.last() }
}

/**
 * Remaining lockout (seconds) given [failures] and how long ago the last failure was — the screen
 * disables input + shows the countdown while > 0. Pure so the throttle is unit-testable without a clock.
 */
fun unlockRemainingLockoutSeconds(failures: Int, secondsSinceLastFailure: Long): Long {
    val total = unlockLockoutSeconds(failures)
    if (total == 0L) return 0L
    return (total - secondsSinceLastFailure).coerceAtLeast(0L)
}
