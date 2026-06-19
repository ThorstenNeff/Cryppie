package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.RpcEndpoint
import com.tneff.cyppie.storage.SeedSession
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletcore.AccountManager
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.TokenCatalog
import com.tneff.cyppie.walletcore.WalletRepository

private enum class WalletDest { Home, Receive, AddToken, Nfts }

/**
 * Public dev/test RPC endpoints (no API key). Release builds inject Alchemy/Infura keys via build-config
 * (KAN-103 follow-up); these public nodes serve the read paths (balances / `eth_call`) meanwhile.
 */
private val defaultRpcByChain: Map<Long, EvmRpcClient> by lazy {
    mapOf(
        EvmChain.ETHEREUM.chainId to EvmRpcClient.create(
            listOf(RpcEndpoint("publicnode", "https://ethereum-rpc.publicnode.com")),
        ),
        EvmChain.BASE.chainId to EvmRpcClient.create(
            listOf(RpcEndpoint("publicnode", "https://base-rpc.publicnode.com")),
        ),
    )
}

private fun buildRepository(seedSource: SeedSource): WalletRepository =
    WalletRepository(AccountManager(EvmKeyManager(seedSource)), defaultRpcByChain)

/**
 * KAN-103 — the live wallet shell behind the app-shell Home destination. Builds a [WalletRepository]
 * from the unlocked [SeedSession] (account derivation + read RPC) and hosts Home → Receive / Add-token.
 * Curated [TokenCatalog] tokens are queried per chain (per-token decimals in the rows, KAN-89 M1 gate).
 * Non-web (no seed on web); if the session is somehow gone, it asks the shell to re-lock.
 */
@Composable
fun WalletShell(onLock: () -> Unit) {
    val seedSource = SeedSession.current
    if (seedSource == null) {
        LaunchedEffect(Unit) { onLock() }
        return
    }
    val repository = remember(seedSource) { buildRepository(seedSource) }
    val tokensByChain = remember {
        EvmChain.entries.associateWith { chain -> TokenCatalog.forChain(chain).map { it.address } }
    }
    val viewModel: WalletHomeViewModel = viewModel { WalletHomeViewModel(repository, tokensByChain) }
    var dest by rememberSaveable { mutableStateOf(WalletDest.Home) }

    when (dest) {
        WalletDest.Home -> WalletHomeScreen(
            onReceive = { dest = WalletDest.Receive },
            onAddToken = { dest = WalletDest.AddToken },
            onNfts = { dest = WalletDest.Nfts },
            viewModel = viewModel,
        )
        WalletDest.Receive -> {
            // Same EVM address across chains (KAN-78); the screen re-labels per chain itself.
            val info = remember(viewModel.selectedAccount) {
                repository.receiveInfo(viewModel.selectedAccount, EvmChain.ETHEREUM)
            }
            ReceiveScreen(address = info.address.value, onBack = { dest = WalletDest.Home })
        }
        WalletDest.AddToken -> AddTokenScreen(
            chain = EvmChain.ETHEREUM,
            onResolve = { address, chain -> repository.resolveErc20(address, chain) },
            // Persisting the added token (so it shows on Home) is a follow-up; curated tokens show now.
            onAdd = { dest = WalletDest.Home },
            onBack = { dest = WalletDest.Home },
        )
        WalletDest.Nfts -> {
            // NFTs for the currently selected account (PRD-03 read-only; KAN-105). MVP is **Ethereum
            // only** by design — unlike Home balances (ETH + Base), the collectibles grid has no chain
            // selector yet; Base/multi-chain NFTs are a deliberate follow-up (needs a chain switcher UI).
            val account = viewModel.selectedAccount
            val nftViewModel: NftViewModel =
                viewModel(key = "nft_${account}") { NftViewModel(repository, account, EvmChain.ETHEREUM) }
            NftGridScreen(onBack = { dest = WalletDest.Home }, viewModel = nftViewModel)
        }
    }
}
