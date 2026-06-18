package com.tneff.cyppie.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class UnlockThrottleTest {

    @Test
    fun no_lockout_below_threshold() {
        for (f in 0 until UNLOCK_LOCKOUT_THRESHOLD) {
            assertEquals(0L, unlockLockoutSeconds(f), "no lockout at $f failures")
        }
    }

    @Test
    fun exponential_backoff_schedule() {
        assertEquals(30L, unlockLockoutSeconds(5))
        assertEquals(60L, unlockLockoutSeconds(6))
        assertEquals(300L, unlockLockoutSeconds(7))
        assertEquals(900L, unlockLockoutSeconds(8))
        assertEquals(1800L, unlockLockoutSeconds(9))
    }

    @Test
    fun backoff_caps_at_last_value() {
        assertEquals(1800L, unlockLockoutSeconds(20))
    }

    @Test
    fun remaining_counts_down_and_floors_at_zero() {
        assertEquals(30L, unlockRemainingLockoutSeconds(5, 0))
        assertEquals(10L, unlockRemainingLockoutSeconds(5, 20))
        assertEquals(0L, unlockRemainingLockoutSeconds(5, 30))
        assertEquals(0L, unlockRemainingLockoutSeconds(5, 99))
    }

    @Test
    fun remaining_zero_below_threshold() {
        assertEquals(0L, unlockRemainingLockoutSeconds(4, 0))
    }
}
