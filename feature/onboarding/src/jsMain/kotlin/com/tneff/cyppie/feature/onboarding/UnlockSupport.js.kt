package com.tneff.cyppie.feature.onboarding

// Web has no local seed (read-only) — no unlock.
actual object UnlockSupport {
    actual val available: Boolean = false
    actual val isUnlocked: Boolean = false
    actual suspend fun unlock(password: String): UnlockOutcome = UnlockOutcome.NoWallet
    actual fun lock() {}
}
