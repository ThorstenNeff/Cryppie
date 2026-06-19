package com.tneff.cyppie

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.tneff.cyppie.walletconnect.WcTransportOverride
import com.tneff.cyppie.walletconnect.e2e.WcE2e

/**
 * DEBUG-ONLY (KAN-126 L3 / KAN-127 WC E2E harness). Handles `cyppie://wc-e2e?script=<json>`: builds a
 * deterministic [com.tneff.cyppie.walletconnect.e2e.FakeWalletConnectController] from the script and
 * installs it via [WcTransportOverride] so the WalletConnect UI runs against the fake transport, then
 * launches the app. This whole source set (and the `:walletconnect-e2e` module) is `debugImplementation`
 * only, so neither this activity, the deep-link, nor the fake exist in the release binary.
 */
class WcE2eActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val script = intent?.data?.getQueryParameter("script")
        // isDebugBuild = true: this code only compiles into the debug build (src/debug). WcE2e also
        // enforces a never-mainnet guard, so a release/mainnet script can never activate the fake.
        WcTransportOverride.transport = WcE2e.fakeOrNull(script, isDebugBuild = true)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
