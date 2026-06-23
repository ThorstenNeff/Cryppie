package com.tneff.cyppie.feature.wallet

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tneff.cyppie.evm.network.NetworkEnvironment
import com.tneff.cyppie.evm.network.NetworkPreferenceStore

@Composable
internal actual fun rememberNetworkPreferenceStore(): NetworkPreferenceStore {
    val appContext = LocalContext.current.applicationContext
    return remember { AndroidNetworkPreferenceStore(appContext) }
}

/** Android `SharedPreferences`-backed active-network persistence (plain preference, no crypto). */
private class AndroidNetworkPreferenceStore(context: Context) : NetworkPreferenceStore {
    private val prefs = context.getSharedPreferences("cyppie_network", Context.MODE_PRIVATE)

    override fun loadEnvironment(): NetworkEnvironment? =
        prefs.getString(KEY, null)?.let { runCatching { NetworkEnvironment.valueOf(it) }.getOrNull() }

    override fun saveEnvironment(env: NetworkEnvironment) {
        prefs.edit().putString(KEY, env.name).apply()
    }

    private companion object { const val KEY = "active_env" }
}
