package com.tneff.cyppie.feature.onboarding

/** Result of a returning-user unlock attempt (KAN-92). */
enum class UnlockOutcome { Success, WrongPassword, NoWallet, Error }

/**
 * Returning-user unlock seam (KAN-92): decrypts the stored seed with the app password and holds the
 * resulting `SecureSeedSource` as the in-memory session (consumed by the live wallet repo; cleared by
 * [lock] on auto-lock / background). Non-web (`:storage`/`SeedVault`); js/wasm report [available] =
 * false (web has no local seed → no unlock). Password is zeroized after use; errors reveal nothing.
 */
expect object UnlockSupport {
    /** Whether local unlock is possible on this target (false on web). */
    val available: Boolean

    /** Whether a seed session is currently unlocked (held in memory). */
    val isUnlocked: Boolean

    suspend fun unlock(password: String): UnlockOutcome

    /** Clears the in-memory seed session (auto-lock). */
    fun lock()
}
