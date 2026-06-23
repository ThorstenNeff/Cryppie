package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.walletcore.ChainBalances
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.WalletRepository
import kotlinx.coroutines.launch

/** How many BIP-44 accounts to surface in the switcher (MVP). */
private const val ACCOUNT_COUNT = 5

/** Wallet-Home UI state (KAN-81). Read-only; no fiat valuation (PRD-02). */
sealed interface WalletHomeUiState {
    data object Loading : WalletHomeUiState
    data class Content(val balances: List<ChainBalances>, val degraded: Boolean) : WalletHomeUiState
    data object Empty : WalletHomeUiState
    data object Error : WalletHomeUiState
}

/**
 * Wallet-Home flow controller (KAN-81): lists BIP-44 accounts (addresses via L1) and loads per-chain
 * native + ERC-20 balances through `:walletcore` [WalletRepository.accountPortfolio]. Read-only.
 *
 * The repository is injected (DI) — its construction with an unlocked `SeedSource` + RPC config is the
 * app-shell wiring (KAN-89). Account derivation / portfolio failures degrade gracefully to
 * [WalletHomeUiState.Empty] / [WalletHomeUiState.Error] (no crash, password gate elsewhere).
 */
class WalletHomeViewModel(
    private val repository: WalletRepository,
    private val tokensByChain: Map<EvmChain, List<EvmAddress>> = emptyMap(),
) : ViewModel() {

    /** Derived account addresses (empty if the wallet can't be read yet → Empty state). */
    val accounts: List<EvmAccount> = runCatching { repository.accounts(ACCOUNT_COUNT) }.getOrElse { emptyList() }

    var selectedAccount: Int by mutableStateOf(0)
        private set

    var uiState: WalletHomeUiState by mutableStateOf(WalletHomeUiState.Loading)
        private set

    init {
        load()
    }

    fun selectAccount(index: Int) {
        if (index in accounts.indices && index != selectedAccount) {
            selectedAccount = index
            load()
        }
    }

    fun refresh() = load()

    private fun load() {
        if (accounts.isEmpty()) {
            uiState = WalletHomeUiState.Empty
            return
        }
        viewModelScope.launch {
            uiState = WalletHomeUiState.Loading
            uiState = runCatching {
                // KAN-173: use the (profile-scoped) tokensByChain as-is — don't re-iterate EvmChain.entries
                // (which would pull in testnet chains once Dev-2 adds them; the shell already scopes this map
                // to the active env's chains, keeping mainnet byte-identical).
                val tokens = tokensByChain
                val balances = repository.accountPortfolio(selectedAccount, tokens)
                if (balances.isEmpty()) {
                    WalletHomeUiState.Empty
                } else {
                    WalletHomeUiState.Content(balances, degraded = balances.any { it.degraded })
                }
            }.getOrElse { WalletHomeUiState.Error }
        }
    }
}
