package com.tneff.cyppie

import android.app.Application
import com.tneff.cyppie.storage.AndroidStoragePaths
import com.tneff.cyppie.walletconnect.WalletConnectInitializer

/**
 * App Application — initializes `:storage`'s app-private dir once, before any entry point/process
 * restart reaches the Activity (KAN-89/95 L1), so `defaultSeedFilePath()` always resolves; and the
 * WalletConnect/Reown singletons (CoreClient/WalletKit) which need the `Application` context (ADR-0015).
 */
class CyppieApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidStoragePaths.init(filesDir.path)

        // WalletConnect/Reown init (KAN-131). projectId from build-config (`wcProjectId`, out-of-repo);
        // empty → relay can't pair but the app runs fine (no crash). Metadata mirrors the iOS shim.
        WalletConnectInitializer.initialize(
            application = this,
            projectId = BuildConfig.WC_PROJECT_ID,
            appName = "Cyppie",
            appDescription = "Cyppie — non-custodial wallet",
            appUrl = "https://cyppie.com",
            appIcons = listOf("https://cyppie.com/icon.png"),
            redirect = "cyppie://",
        )
    }
}
