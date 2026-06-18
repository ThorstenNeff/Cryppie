package com.tneff.cyppie.storage

import com.tneff.cyppie.wallet.SeedSource

/**
 * The in-memory unlocked seed session (KAN-103): set when the user unlocks, read by the wallet repo
 * (account derivation / signing), cleared on auto-lock / background. One source of truth for "is a
 * seed currently unlocked, and which one" — so unlock, the wallet shell, and lock all agree. Non-web
 * (`:storage` has no seed on web). Not thread-safe by design: mutated from the UI/main flow only.
 */
object SeedSession {
    private var source: SecureSeedSource? = null

    /** The unlocked seed source, or null when locked. */
    val current: SeedSource? get() = source

    val isActive: Boolean get() = source != null

    /** Installs a freshly unlocked seed source, closing (zeroizing) any previous one. */
    fun set(newSource: SecureSeedSource) {
        if (newSource !== source) source?.close()
        source = newSource
    }

    /** Clears + zeroizes the session (auto-lock / sign-out). */
    fun clear() {
        source?.close()
        source = null
    }
}
