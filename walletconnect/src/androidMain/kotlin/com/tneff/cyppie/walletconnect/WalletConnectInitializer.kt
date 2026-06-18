package com.tneff.cyppie.walletconnect

import android.app.Application
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit

/**
 * One-time WalletConnect/Reown init — call from the app `Application.onCreate` (Reown needs the
 * `Application` context; ADR-0015). The [projectId] comes from build-config and is **never committed**
 * (PRD-02 §9). After this, construct [WalletConnectController] (binds to the WalletKit singleton).
 */
object WalletConnectInitializer {

    fun initialize(
        application: Application,
        projectId: String,
        appName: String,
        appDescription: String,
        appUrl: String,
        appIcons: List<String> = emptyList(),
        redirect: String? = null,
        onError: (String) -> Unit = {},
    ) {
        CoreClient.initialize(
            application = application,
            projectId = projectId,
            connectionType = ConnectionType.AUTOMATIC,
            metaData = Core.Model.AppMetaData(
                name = appName,
                description = appDescription,
                url = appUrl,
                icons = appIcons,
                redirect = redirect,
            ),
            onError = { error -> onError(error.throwable.message ?: "Core init failed") },
        )
        WalletKit.initialize(
            params = Wallet.Params.Init(core = CoreClient),
            onSuccess = {},
            onError = { error -> onError(error.throwable.message ?: "WalletKit init failed") },
        )
    }
}
