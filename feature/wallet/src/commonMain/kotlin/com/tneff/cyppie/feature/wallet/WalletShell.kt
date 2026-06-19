package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.feature.portfolio.PortfolioOverview
import com.tneff.cyppie.feature.portfolio.PortfolioOverviewScreen
import com.tneff.cyppie.feature.portfolio.PortfolioOverviewViewModel
import com.tneff.cyppie.portfolio.AlchemyPriceSource
import com.tneff.cyppie.portfolio.PortfolioConfig
import com.tneff.cyppie.portfolio.PortfolioService
import com.tneff.cyppie.portfolio.TokenKey
import com.tneff.cyppie.rpc.AlchemyDataClient
import com.tneff.cyppie.rpc.AlchemyNetworks
import com.tneff.cyppie.rpc.AlchemyNftClient
import com.tneff.cyppie.rpc.AlchemyPriceClient
import com.tneff.cyppie.rpc.AlchemyProxyConfig
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.NftReadClient
import com.tneff.cyppie.rpc.RpcEndpoint
import com.tneff.cyppie.send.SendOrchestrator
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedSession
import com.tneff.cyppie.storage.SeedVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletcore.AccountManager
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.TokenCatalog
import com.tneff.cyppie.walletcore.WalletRepository

private enum class WalletDest { Home, Receive, AddToken, Nfts, Send, Portfolio }

/**
 * KAN-112/ADR-0021: all Alchemy/RPC traffic goes through the local `:server` key-proxy — the API key
 * is injected server-side, so **no key ships in the client binary**. Dev default below; release injects
 * the deployed proxy URL via build-config (follow-up). If the proxy is down / its key is unconfigured
 * it answers 503 → the clients map that to `AllProvidersFailed` and the UI degrades cleanly (FR-4).
 * (Android-emulator caveat: the host proxy is reachable at `10.0.2.2:8080`, not `localhost`.)
 */
private const val PROXY_BASE_URL = "http://localhost:8080"
private val alchemyProxy = AlchemyProxyConfig(PROXY_BASE_URL)

private val defaultRpcByChain: Map<Long, EvmRpcClient> by lazy {
    AlchemyNetworks.supportedChainIds.associateWith { chainId ->
        EvmRpcClient.create(listOf(RpcEndpoint("proxy", alchemyProxy.rpcUrl(chainId))))
    }
}

/** NFT read clients per chain via the proxy (KAN-105 grid now has real data through the key-proxy). */
private val defaultNftByChain: Map<Long, NftReadClient> by lazy {
    AlchemyNetworks.supportedChainIds.associateWith { chainId ->
        AlchemyNftClient(chainId, alchemyProxy.nftBaseUrl(chainId))
    }
}

private fun buildRepository(seedSource: SeedSource): WalletRepository =
    WalletRepository(AccountManager(EvmKeyManager(seedSource)), defaultRpcByChain, defaultNftByChain)

/** Curated tokens + native ETH per supported chain — the priced/displayed set (KAN-90 known-good). */
private val portfolioKnownGood: Set<TokenKey> by lazy {
    AlchemyNetworks.supportedChainIds.flatMap { chainId ->
        val curated = EvmChain.fromChainId(chainId)
            ?.let { chain -> TokenCatalog.forChain(chain).map { TokenKey(chainId, it.address) } }
            .orEmpty()
        curated + TokenKey(chainId, null) // native ETH
    }.toSet()
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun nowEpochSeconds(): Long = kotlin.time.Clock.System.now().epochSeconds

/**
 * KAN-114 — the platform portfolio assembler (PRD-03): prices the [accounts]' holdings via the proxy
 * Alchemy Data + Prices clients + native balances (RPC), through [PortfolioService]. Unrealized P&L is
 * left null for now (needs FIFO cost-basis over full transfer history — RichPortfolio/KAN-102 follow-up).
 * Proxy/RPC down → the clients fail → the VM maps it to Error (FR-4 graceful, no crash).
 */
private fun buildPortfolioLoader(accounts: () -> List<EvmAddress>): suspend () -> PortfolioOverview {
    val dataClient = AlchemyDataClient(alchemyProxy.dataBaseUrl())
    val priceSource = AlchemyPriceSource(AlchemyPriceClient(alchemyProxy.pricesBaseUrl())) { nowEpochSeconds() }
    val service = PortfolioService(
        fetchErc20Holdings = { holders, chainIds -> dataClient.tokenHoldings(holders, chainIds) },
        fetchNativeBalance = { chainId, account -> defaultRpcByChain.getValue(chainId).getBalance(account) },
        priceSource = priceSource,
        config = PortfolioConfig(knownGood = portfolioKnownGood, currency = "usd"),
        clockEpochSeconds = { nowEpochSeconds() },
    )
    return {
        val portfolio = service.load(accounts(), AlchemyNetworks.supportedChainIds, vs = "usd")
        PortfolioOverview(portfolio, pnl = null)
    }
}

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
            onSend = { if (viewModel.accounts.isNotEmpty()) dest = WalletDest.Send },
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
        WalletDest.Send -> {
            // Send for the selected account (KAN-110). The orchestrator completes + signs the tx; the
            // ambient unlocked session signs (a non-closeable wrapper keeps it alive — see SendViewModel).
            val account = viewModel.selectedAccount
            val sendViewModel: SendViewModel = viewModel(key = "send_${account}") {
                SendViewModel(
                    repository = repository,
                    orchestrator = SendOrchestrator(defaultRpcByChain),
                    feeData = { chain -> defaultRpcByChain.getValue(chain.chainId).getFeeData() },
                    awaitReceipt = { chain, hash -> defaultRpcByChain.getValue(chain.chainId).awaitReceipt(hash).status },
                    // Re-auth (ADR-0009): a correct password decrypts a FRESH per-sign source off-Main;
                    // signAndBroadcast signs with it and zeroizes it right after (M1). null = wrong password.
                    reauth = { pw ->
                        withContext(Dispatchers.Default) {
                            runCatching { SeedVault(CiphertextStore.defaultFile()).unlock(pw) }.getOrNull()
                        }
                    },
                    accounts = viewModel.accounts,
                    accountIndex = account,
                )
            }
            SendFlow(viewModel = sendViewModel, onExit = { dest = WalletDest.Home })
        }
        WalletDest.Portfolio -> {
            // PF-1 overview (KAN-107) over the live proxy assembler; aggregates the shown accounts.
            val pfViewModel: PortfolioOverviewViewModel = viewModel(key = "portfolio") {
                PortfolioOverviewViewModel(buildPortfolioLoader { viewModel.accounts.map { it.address } })
            }
            PortfolioOverviewScreen(
                state = pfViewModel.uiState,
                onRetry = pfViewModel::refresh,
                onBack = { dest = WalletDest.Home },
            )
        }
    }
}
