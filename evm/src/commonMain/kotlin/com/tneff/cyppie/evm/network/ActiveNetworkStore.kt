package com.tneff.cyppie.evm.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistence port for the active-network selection (one tiny KV). The app supplies the platform `actual`
 * (Android `SharedPreferences` / iOS `NSUserDefaults` / desktop file); `:evm` stays platform-free and the store
 * is unit-testable with [InMemoryNetworkPreferenceStore]. NOT a secret — plain preference, no `:storage` crypto.
 */
interface NetworkPreferenceStore {
    /** The persisted env, or `null` if never chosen (→ the store's default). */
    fun loadEnvironment(): NetworkEnvironment?
    fun saveEnvironment(env: NetworkEnvironment)
}

/** In-memory [NetworkPreferenceStore] — the default (no persistence) + the test double. */
class InMemoryNetworkPreferenceStore(private var value: NetworkEnvironment? = null) : NetworkPreferenceStore {
    override fun loadEnvironment(): NetworkEnvironment? = value
    override fun saveEnvironment(env: NetworkEnvironment) { value = env }
}

/**
 * The reactive, persisted holder of the active [NetworkEnvironment] (ADR-0027 + Dev-1 contract 2026-06-23).
 *
 * [environment] is a **[StateFlow]** so the app-shell can `key(store.environment.collectAsState()){ WalletShell() }`
 * — a [switch] emits a new value, the `key` changes, and the entire VM/DI graph is recreated bound to the new env.
 * That is the **soft-relaunch state-reset**: no per-VM reload wiring, no stale cross-net display, session lists
 * naturally re-fetch for the active net.
 *
 * Loads the persisted env at construction (defaulting to [default], normally [NetworkEnvironment.MAINNET]); every
 * [switch] persists first, then emits — so a process death right after a switch restores the new env.
 */
class ActiveNetworkStore(
    private val preferences: NetworkPreferenceStore = InMemoryNetworkPreferenceStore(),
    default: NetworkEnvironment = NetworkEnvironment.MAINNET,
) {
    private val _environment = MutableStateFlow(preferences.loadEnvironment() ?: default)

    /** The observable active environment — the shell keys its scaffold on this for the soft-relaunch. */
    val environment: StateFlow<NetworkEnvironment> = _environment.asStateFlow()

    /** The active [NetworkProfile] — the single source for every per-env value (slugs/addresses/URLs). */
    val profile: NetworkProfile get() = _environment.value.profile

    /** Switch the active env: **persist, then emit**. No-op (no emission) when already on [env]. */
    fun switch(env: NetworkEnvironment) {
        if (_environment.value == env) return
        preferences.saveEnvironment(env)
        _environment.value = env
    }
}
