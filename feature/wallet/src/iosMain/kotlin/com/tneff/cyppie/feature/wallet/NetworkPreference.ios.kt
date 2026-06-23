package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.evm.network.NetworkEnvironment
import com.tneff.cyppie.evm.network.NetworkPreferenceStore
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberNetworkPreferenceStore(): NetworkPreferenceStore =
    remember { IosNetworkPreferenceStore() }

/** iOS `NSUserDefaults`-backed active-network persistence (plain preference, no crypto). */
private class IosNetworkPreferenceStore : NetworkPreferenceStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun loadEnvironment(): NetworkEnvironment? =
        defaults.stringForKey(KEY)?.let { runCatching { NetworkEnvironment.valueOf(it) }.getOrNull() }

    override fun saveEnvironment(env: NetworkEnvironment) {
        defaults.setObject(env.name, forKey = KEY)
    }

    private companion object { const val KEY = "cyppie_active_env" }
}
