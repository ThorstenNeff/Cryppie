package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.aa.AaSigner
import com.tneff.cyppie.aa.DcaEnableBuilder
import com.tneff.cyppie.aa.KtorDcaApi
import com.tneff.cyppie.feature.copy.CopyRoot
import com.tneff.cyppie.feature.copy.StubFollowGrantService
import com.tneff.cyppie.evm.Hex
import dev.whyoleg.cryptography.random.CryptographyRandom
import com.tneff.cyppie.auth.AuthSession
import com.tneff.cyppie.auth.Eip191SiweSigner
import com.tneff.cyppie.auth.InMemoryTokenVault
import com.tneff.cyppie.auth.KeycloakClient
import com.tneff.cyppie.auth.SiweMessage
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.dca.DcaGrantParams
import com.tneff.cyppie.feature.dca.DcaOverviewScreen
import com.tneff.cyppie.feature.dca.DcaViewModel
import com.tneff.cyppie.feature.dca.GrantScreen
import com.tneff.cyppie.feature.dca.GrantViewModel
import kotlinx.coroutines.launch
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
import com.tneff.cyppie.feature.market.MarketOverviewScreen
import com.tneff.cyppie.feature.market.MarketOverviewViewModel
import com.tneff.cyppie.feature.market.MarketScreen
import com.tneff.cyppie.feature.market.MarketViewModel
import com.tneff.cyppie.feature.market.WatchedAsset
import com.tneff.cyppie.market.AlchemyPriceSource
import com.tneff.cyppie.portfolio.toMarketAsset
import com.tneff.cyppie.portfolio.ApproxReason
import com.tneff.cyppie.portfolio.Metric
import com.tneff.cyppie.market.BinanceMarketClient
import com.tneff.cyppie.market.BridgeMarketDataApi
import com.tneff.cyppie.market.CoinGeckoMarketClient
import com.tneff.cyppie.market.MarketAsset
import com.tneff.cyppie.market.MarketDataApi
import com.tneff.cyppie.market.Money
import com.tneff.cyppie.portfolio.Portfolio
import com.tneff.cyppie.portfolio.PortfolioConfig
import com.tneff.cyppie.portfolio.PortfolioPerformance
import com.tneff.cyppie.portfolio.PortfolioService
import com.tneff.cyppie.portfolio.PortfolioTimeSeries
import com.tneff.cyppie.portfolio.RichPortfolio
import com.tneff.cyppie.portfolio.TokenKey
import com.tneff.cyppie.rpc.AlchemyDataClient
import com.tneff.cyppie.rpc.AlchemyNetworks
import com.tneff.cyppie.rpc.AlchemyNftClient
import com.tneff.cyppie.rpc.AlchemyPriceClient
import com.tneff.cyppie.rpc.AlchemyTransfersClient
import com.tneff.cyppie.rpc.AlchemyProxyConfig
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.NftReadClient
import com.tneff.cyppie.rpc.RpcEndpoint
import com.tneff.cyppie.send.SendOrchestrator
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedSession
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.walletconnect.WalletConnectController
import com.tneff.cyppie.walletconnect.WcTransportOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletcore.AccountManager
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.TokenCatalog
import com.tneff.cyppie.walletcore.WalletRepository

private enum class WalletDest { Home, Receive, AddToken, Nfts, Send, Portfolio, WalletConnect, Market, MarketDetail, Dca, DcaGrant, Copy }

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

/** KAN-131 — the live market-data API: [BridgeMarketDataApi] over the proxy CoinGecko (key injected
 *  server-side) + keyless Binance. No CoinGecko key → 503 → MD screens degrade cleanly (FR-4); Binance
 *  candles still render. */
private val marketDataApi: MarketDataApi by lazy {
    BridgeMarketDataApi(
        coinGecko = CoinGeckoMarketClient(alchemyProxy.coinGeckoBaseUrl()),
        binance = BinanceMarketClient(),
        clockEpochSeconds = ::nowEpochSeconds,
    )
}

/** MD-3 watchlist (curated MVP): native ETH + the chain-1 catalog majors. Labels are display-only;
 *  assets the market catalog can't map degrade per-row (FR-4) — the row still renders. */
private val marketWatchlist: List<WatchedAsset> by lazy {
    val eth = WatchedAsset(MarketAsset.Native(1L), "Ethereum")
    val erc20s = EvmChain.fromChainId(1L)?.let { chain ->
        TokenCatalog.forChain(chain).take(4).map { WatchedAsset(MarketAsset.Erc20(1L, it.address), it.symbol) }
    }.orEmpty()
    listOf(eth) + erc20s
}

/**
 * PRD-05 Ph1 (KAN-138/KAN-141) — DCA / AA endpoints.
 * - [KEYCLOAK_BASE_URL]: the SIWE realm (KAN-141, real).
 * - [USER_SERVICE_BASE_URL]: the JWT User-Service (Ph0 §4) — per the runbook its paths live under
 *   `auth.cyppie.com/v1/...` (api.cyppie.com is the Alchemy key-proxy, a different host). The exact base
 *   URL is fixed at deploy; until then a 404/unreachable surface maps to Error (FR-4 graceful), exactly
 *   like Market degrades on a 503. The nav + SIWE flow are live; only the live data awaits the backend.
 */
private const val KEYCLOAK_BASE_URL = "https://auth.cyppie.com"
private const val USER_SERVICE_BASE_URL = "https://auth.cyppie.com"

/** The fixed DCA grant routing/token config (MVP). The real allowed router/selector/spend-token are
 *  backend-published (Ph1); these mainnet defaults drive the grant UX + disclosure until then. */
private val dcaGrantParams: DcaGrantParams by lazy {
    DcaGrantParams(
        chainId = 1L,
        router = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45",     // Uniswap UniversalRouter (mainnet) — placeholder
        swapSelector = "0x3593564c",                                // execute(bytes,bytes[],uint256)
        spendToken = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", // USDC (mainnet)
        spendTokenDecimals = 6,                                      // USDC = 6
    )
}

/** RFC3339 UTC (…Z) — AuthSession pins SIWE `issuedAt` to UTC (P1-9); the stdlib Instant renders as `…Z`. */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun iso8601Utc(epochSeconds: Long): String = kotlin.time.Instant.fromEpochSeconds(epochSeconds).toString()

/** Daily price-history window for FIFO cost-basis (covers most holding ages; older buys approximate). */
private const val PNL_HISTORY_WINDOW_SECONDS = 3L * 365 * 86_400
private const val ONE_DAY_SECONDS = 86_400L

/**
 * KAN-114/KAN-124 — the platform portfolio assembler (PRD-03): prices the [accounts]' holdings via the
 * proxy Alchemy Data + Prices clients + native balances (RPC), through [PortfolioService], then layers
 * on live unrealized P&L via [computePnl] (RichPortfolio/KAN-102 FIFO cost-basis). P&L is **best-effort**
 * — any failure leaves pnl null and the priced overview still renders. Proxy/RPC down → the clients fail
 * → the VM maps it to Error (FR-4 graceful, no crash).
 */
private fun buildPortfolioLoader(accounts: () -> List<EvmAddress>): suspend () -> PortfolioOverview {
    val dataClient = AlchemyDataClient(alchemyProxy.dataBaseUrl())
    val priceSource = AlchemyPriceSource(AlchemyPriceClient(alchemyProxy.pricesBaseUrl())) { nowEpochSeconds() }
    val transfersByChain = AlchemyNetworks.supportedChainIds.associateWith { chainId ->
        AlchemyTransfersClient(alchemyProxy.rpcUrl(chainId), chainId)
    }
    val service = PortfolioService(
        fetchErc20Holdings = { holders, chainIds -> dataClient.tokenHoldings(holders, chainIds) },
        fetchNativeBalance = { chainId, account -> defaultRpcByChain.getValue(chainId).getBalance(account) },
        priceSource = priceSource,
        config = PortfolioConfig(knownGood = portfolioKnownGood, currency = "usd"),
        clockEpochSeconds = { nowEpochSeconds() },
    )
    return {
        val portfolio = service.load(accounts(), AlchemyNetworks.supportedChainIds, vs = "usd")
        // P&L + 24h are heavier (full transfer history + price history per holding) + must never block the
        // priced overview; a failure (proxy down, no history) leaves them null and FR-9-≈ renders cleanly.
        runCatching { computePerformance(portfolio, priceSource, transfersByChain) }
            .getOrElse { PortfolioOverview(portfolio, pnl = null) }
    }
}

/**
 * KAN-124 — live performance over the valued [portfolio]: unrealized P&L (current value − FIFO
 * cost-basis, RichPortfolio/KAN-102) + 24h change (current holdings × the 24h price delta). Per holding:
 * full transfer history (paged) + a daily price history; native ETH is priced via the chain's WETH using
 * [PortfolioService.priceTokenFor] — the SAME single source the valuation uses (review M1: no duplicate
 * address table to drift). A holding with no priceable token is skipped (matches the valuation, which
 * also can't price it). FR-9: P&L is Approximate (COST_BASIS_AMBIGUITY; + INCOMPLETE_TRANSFERS when
 * page-capped). cents (MONEY_SCALE) throughout. pnl null if no current value.
 */
private suspend fun computePerformance(
    portfolio: Portfolio,
    priceSource: AlchemyPriceSource,
    transfersByChain: Map<Long, AlchemyTransfersClient>,
): PortfolioOverview {
    val currentValue = portfolio.totalValue?.value
    if (currentValue == null || portfolio.holdings.isEmpty()) return PortfolioOverview(portfolio, pnl = null)
    val now = nowEpochSeconds()
    var totalCostMinor = 0L
    var anyTruncated = false
    val pricesNow = HashMap<com.tneff.cyppie.portfolio.PortfolioToken, Money>()
    val prices24hAgo = HashMap<com.tneff.cyppie.portfolio.PortfolioToken, Money>()
    // L1 (review): fetch the transfer history ONCE per (account, chain) and reuse it across that group's
    // holdings — transfers are chain-scoped, so per-holding fetches would re-pull the same history N× and
    // risk tripping the proxy rate-limit. tokenCostBasis filters the shared transfers by token internally.
    val byAccountChain = portfolio.holdings.groupBy { it.account to it.token.chainId }
    for ((accountChain, holdings) in byAccountChain) {
        val (account, chainId) = accountChain
        val client = transfersByChain[chainId] ?: continue
        val history = RichPortfolio.fullHistory(
            account,
            fetchTransfers = { addr, dir, key -> client.assetTransfers(addr, dir, pageKey = key) },
        )
        anyTruncated = anyTruncated || history.truncated
        for (h in holdings) {
            val priceToken = PortfolioService.priceTokenFor(h.token) ?: continue // unpriceable chain → skip (as the valuation does)
            val priceHistory = priceSource.priceHistory(
                priceToken.toMarketAsset(), currentValue.currency, now - PNL_HISTORY_WINDOW_SECONDS, now, ONE_DAY_SECONDS,
            )
            val cb = RichPortfolio.tokenCostBasis(h.token, h.account, history.transfers, priceHistory, history.truncated)
            // Saturate rather than wrap on an absurd sum (same family as PortfolioPerformance's saturatingSub).
            totalCostMinor = if (totalCostMinor > Long.MAX_VALUE - cb.costBasisCents) Long.MAX_VALUE else totalCostMinor + cb.costBasisCents
            // 24h change inputs (keyed by the holding's own token): nearest price at/near now vs 24h ago.
            RichPortfolio.priceAt(priceHistory, now)?.let { pricesNow[h.token] = it }
            RichPortfolio.priceAt(priceHistory, now - ONE_DAY_SECONDS)?.let { prices24hAgo[h.token] = it }
        }
    }
    val costBasis = Money(totalCostMinor, currentValue.scale, currentValue.currency)
    val pnlExtra = if (anyTruncated) listOf(ApproxReason.INCOMPLETE_TRANSFERS) else emptyList()
    val pnl = PortfolioPerformance.unrealizedPnl(currentValue, costBasis, pnlExtra)
    val change24h = PortfolioTimeSeries.change24h(portfolio.holdings, pricesNow, prices24hAgo, currentValue.currency)
    return PortfolioOverview(portfolio.copy(change24h = change24h), pnl)
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
    // The market asset tapped in MD-3, rendered by MD-1 (MarketDetail). Not saveable (transient nav arg).
    var marketAsset by remember { mutableStateOf<WatchedAsset?>(null) }

    // --- DCA / AA graph (PRD-05 Ph1, KAN-141), shared across the Dca + DcaGrant destinations ---
    // owner = account index 0: the SIWE identity AND the AA owner (UserOpSigner.OWNER_ACCOUNT_INDEX).
    val dcaOwner = remember(seedSource) { repository.receiveInfo(0, EvmChain.ETHEREUM).address }
    // The SIWE auth session: holds the (in-memory) JWT + drives nonce→sign→Keycloak→token, with headless
    // refresh (P1-7). Defaults wire the real Keycloak (auth.cyppie.com) + the shared jsonHttpClient.
    val authSession = remember(seedSource) {
        AuthSession(
            keycloak = KeycloakClient(KEYCLOAK_BASE_URL),
            siweSigner = Eip191SiweSigner(),
            vault = InMemoryTokenVault(),
            owner = dcaOwner,
            nowEpochSeconds = ::nowEpochSeconds,
            iso8601 = ::iso8601Utc,
        )
    }
    // The User-Service client: bearer = the session JWT (fail-closed — KtorDcaApi.bearer() throws on blank).
    val dcaApi = remember(seedSource) { KtorDcaApi(USER_SERVICE_BASE_URL, bearerToken = { authSession.token() ?: "" }) }
    val aaSigner = remember { AaSigner() }
    // AA-op signing uses a FRESH per-op re-auth source (explicit consent per funds-moving signature), like
    // Send/WC — distinct from the light post-unlock SIWE consent (ambient session, identity only).
    val dcaReauth: suspend (String) -> SeedSource? = remember {
        { pw -> withContext(Dispatchers.Default) { runCatching { SeedVault(CiphertextStore.defaultFile()).unlock(pw.toCharArray()) }.getOrNull() } }
    }

    when (dest) {
        WalletDest.Home -> WalletHomeScreen(
            onReceive = { dest = WalletDest.Receive },
            onSend = { if (viewModel.accounts.isNotEmpty()) dest = WalletDest.Send },
            onAddToken = { dest = WalletDest.AddToken },
            onNfts = { dest = WalletDest.Nfts },
            onPortfolio = { dest = WalletDest.Portfolio },
            onConnect = { dest = WalletDest.WalletConnect },
            onMarket = { dest = WalletDest.Market },
            onDca = { dest = WalletDest.Dca },
            onCopy = { dest = WalletDest.Copy },
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
        WalletDest.WalletConnect -> {
            // WC-UI (KAN-126): pairing → proposal/request, over the app-embedded :walletconnect controller.
            val wcViewModel: WalletConnectViewModel = viewModel(key = "walletconnect") {
                WalletConnectViewModel(
                    // Debug-only E2E harness override (KAN-126 L3); null in release → the real controller.
                    controller = WcTransportOverride.transport ?: WalletConnectController(),
                    accounts = viewModel.accounts,
                    // Re-auth (ADR-0009): a correct password decrypts a FRESH per-signature source off-Main;
                    // signAndRespond signs with it and closes/zeroizes it immediately (same as Send/M1).
                    reauth = { pw ->
                        withContext(Dispatchers.Default) {
                            runCatching { SeedVault(CiphertextStore.defaultFile()).unlock(pw) }.getOrNull()
                        }
                    },
                    // Et.3b: the same Send pipeline drives WC eth_sendTransaction (prepare → sign → broadcast).
                    sendOrchestrator = SendOrchestrator(defaultRpcByChain),
                )
            }
            WalletConnectRoot(viewModel = wcViewModel, onExit = { dest = WalletDest.Home })
        }
        WalletDest.Market -> {
            // MD-3 watchlist overview over the live BridgeMarketDataApi; tap a row → MD-1 detail.
            val overviewVm: MarketOverviewViewModel = viewModel(key = "market_overview") {
                MarketOverviewViewModel(marketWatchlist, vs = "usd", data = marketDataApi, nowEpochSeconds = ::nowEpochSeconds)
            }
            MarketOverviewScreen(
                viewModel = overviewVm,
                onAssetClick = { asset ->
                    marketAsset = marketWatchlist.firstOrNull { it.asset == asset } ?: WatchedAsset(asset, "")
                    dest = WalletDest.MarketDetail
                },
                onBack = { dest = WalletDest.Home },
            )
        }
        WalletDest.MarketDetail -> {
            val watched = marketAsset
            if (watched == null) {
                dest = WalletDest.Market // no asset selected → back to the list (defensive)
            } else {
                val detailVm: MarketViewModel = viewModel(key = "market_detail_${watched.label}") {
                    MarketViewModel(watched.asset, vs = "usd", data = marketDataApi, nowEpochSeconds = ::nowEpochSeconds)
                }
                MarketScreen(viewModel = detailVm, assetTitle = watched.label, onBack = { dest = WalletDest.Market })
            }
        }
        WalletDest.Dca -> {
            // PRD-05 Ph1 (KAN-138/KAN-141): SIWE-gated DCA overview. First visit → light no-blind consent
            // ("Sign in to Cyppie") → post-unlock SIWE sign-in (ambient session, no 2nd password); then the
            // DCA overview over the JWT User-Service. AA-op signing inside uses fresh re-auth.
            DcaGate(authSession = authSession, ambient = seedSource, onLock = onLock) {
                val dcaVm: DcaViewModel = viewModel(key = "dca_overview") {
                    DcaViewModel(api = dcaApi, signer = aaSigner, owner = dcaOwner, reauth = dcaReauth)
                }
                DcaOverviewScreen(
                    viewModel = dcaVm,
                    onCreate = { dest = WalletDest.DcaGrant },
                    onBack = { dest = WalletDest.Home },
                )
            }
        }
        WalletDest.DcaGrant -> {
            // Smart-Session grant: build the §2 config (no-blind disclosure) → re-auth → on-device enable-sign.
            val grantVm: GrantViewModel = viewModel(key = "dca_grant") {
                GrantViewModel(
                    api = dcaApi,
                    signer = aaSigner,
                    owner = dcaOwner,
                    params = dcaGrantParams,
                    nowEpochSeconds = ::nowEpochSeconds,
                    // The session-enable nonce is on-chain state: read it from the SmartSession module via the
                    // RPC key-proxy (read-only, public) — keeps the enable fully app-built (no backend endpoint).
                    readNonce = { permissionId, account, chainId ->
                        val rpc = defaultRpcByChain.getValue(chainId)
                        val out = rpc.call(EvmAddress.parse(DcaEnableBuilder.SMART_SESSION_ADDRESS), DcaEnableBuilder.nonceCalldata(permissionId, account))
                        DcaEnableBuilder.decodeNonce(out) // N1: 32-byte uint256 or fail-closed (no silent 0)
                    },
                    // App-chosen unique 32-byte session salt (CSPRNG → a unique permissionId per session).
                    genSalt = { "0x" + Hex.encode(CryptographyRandom.nextBytes(32)) },
                    reauth = dcaReauth,
                )
            }
            GrantScreen(viewModel = grantVm, onDone = { dest = WalletDest.Dca }, onBack = { dest = WalletDest.Dca })
        }
        WalletDest.Copy -> {
            // Copy / Follow-Trader flow (KAN-155). FLAG_SECURE over the whole flow (it includes the
            // disclosure+signature context; spec asks for it on Confirm — superset is fine). Stub grant
            // service until Dev-2's KAN-154 lands; owner = account#0 (self-copy guard); fresh re-auth source.
            SecureScreenEffect()
            CopyRoot(
                service = StubFollowGrantService(),
                owner = dcaOwner,
                budgetTokenDecimals = 6, // USDC v1
                reauth = dcaReauth,
                onExit = { dest = WalletDest.Home },
            )
        }
    }
}

/**
 * SIWE sign-in gate (KAN-141): probes the [authSession] for a valid JWT; if present → [content]. If not,
 * shows the **light no-blind consent** ("Sign in to Cyppie") and, on confirm, runs the post-unlock SIWE
 * sign-in with the [ambient] unlocked session wrapped **non-closeable** (the signer zeroizes its source —
 * we must not let it zeroize the live session; close() becomes a no-op). A failed sign-in shows a retry.
 */
@Composable
private fun DcaGate(
    authSession: AuthSession,
    ambient: SeedSource,
    onLock: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var signedIn by remember { mutableStateOf<Boolean?>(null) }
    var signing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val siweSource = remember(ambient) {
        object : SeedSource { override fun <R> withSeed(block: (ByteArray) -> R): R = ambient.withSeed(block) }
    }

    LaunchedEffect(Unit) { signedIn = runCatching { authSession.token() != null }.getOrDefault(false) }

    when (signedIn) {
        true -> content()
        null -> Box(Modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.Center) {
            ProgressRing(diameter = 32.dp)
        }
        false -> Box(Modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(spacing.xl).testTag("dca_siwe_consent"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text(SiweMessage.STATEMENT, style = CryptasaTheme.typography.titleLarge, color = colors.onSurface)
                Text(
                    "Sign a one-time message to ${SiweMessage.DOMAIN} to enable Auto-Invest. No funds move; this only proves you own this wallet.",
                    style = CryptasaTheme.typography.body,
                    color = colors.onSurfaceVariant,
                )
                if (failed) {
                    CryptasaBanner(title = "Sign-in failed. Please try again.", tone = CryptasaBannerTone.Danger, modifier = Modifier.testTag("dca_siwe_error"))
                }
                CryptasaButton(
                    text = if (signing) "Signing in…" else SiweMessage.STATEMENT,
                    enabled = !signing,
                    onClick = {
                        signing = true; failed = false
                        scope.launch {
                            val ambientGone = SeedSession.current == null
                            if (ambientGone) { onLock(); return@launch }
                            val ok = runCatching { authSession.signIn(siweSource) }.isSuccess
                            signing = false
                            if (ok) signedIn = true else failed = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dca_siwe_signin"),
                )
            }
        }
    }
}
