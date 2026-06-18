package com.tneff.cyppie

import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedVault

// A wallet exists iff the shared encrypted-seed file is initialized (KAN-95 defaultFile = the same
// path onboarding's WalletStore persists to). On Android, AndroidStoragePaths.init(filesDir) must run
// at startup first (MainActivity).
internal actual suspend fun walletExists(): Boolean =
    SeedVault(CiphertextStore.defaultFile()).isInitialized()
