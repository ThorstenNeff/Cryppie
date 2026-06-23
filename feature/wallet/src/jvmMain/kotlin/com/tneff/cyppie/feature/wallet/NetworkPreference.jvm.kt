package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.evm.network.NetworkEnvironment
import com.tneff.cyppie.evm.network.NetworkPreferenceStore
import java.util.prefs.Preferences

@Composable
internal actual fun rememberNetworkPreferenceStore(): NetworkPreferenceStore =
    remember { JvmNetworkPreferenceStore() }

/** Desktop `java.util.prefs`-backed active-network persistence (plain preference, no crypto). */
private class JvmNetworkPreferenceStore : NetworkPreferenceStore {
    private val prefs = Preferences.userRoot().node("com/tneff/cyppie/network")

    override fun loadEnvironment(): NetworkEnvironment? =
        prefs.get(KEY, null)?.let { runCatching { NetworkEnvironment.valueOf(it) }.getOrNull() }

    override fun saveEnvironment(env: NetworkEnvironment) {
        prefs.put(KEY, env.name)
    }

    private companion object { const val KEY = "active_env" }
}
