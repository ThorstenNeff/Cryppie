package com.tneff.cyppie.feature.wallet.di

import com.tneff.cyppie.feature.wallet.WalletHomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module of the wallet feature (ADR-0007). Declares the Wallet-Home view model; the lambda form
 * is used (not `viewModelOf`) so the optional `tokensByChain` keeps its default. The `WalletRepository`
 * binding (unlocked `SeedSource` + RPC config) is contributed by the app-shell wiring (KAN-89), which
 * also aggregates this module into `appModules`.
 */
val walletModule: Module = module {
    viewModel { WalletHomeViewModel(get()) }
}
