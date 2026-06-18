package com.tneff.cyppie

import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A wallet exists iff the shared encrypted-seed file is initialized (KAN-95 defaultFile = the same
// path onboarding's WalletStore persists to). File IO runs off the main thread (KAN-89 L2). On
// Android, CyppieApplication.onCreate must run AndroidStoragePaths.init(filesDir) first.
internal actual suspend fun walletExists(): Boolean =
    withContext(Dispatchers.Default) { SeedVault(CiphertextStore.defaultFile()).isInitialized() }
