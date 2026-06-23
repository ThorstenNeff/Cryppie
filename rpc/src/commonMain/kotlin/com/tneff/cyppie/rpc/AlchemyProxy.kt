package com.tneff.cyppie.rpc

/**
 * Chain-id ↔ Alchemy network slug mapping — the single source for the slug duplication (ADR-0027). Sourced from
 * the [com.tneff.cyppie.evm.network.NetworkProfiles] registry; the Alchemy clients delegate here so there is one
 * map, not four. Slug values are per-chain (disjoint across envs), so [of]/[chainId] resolve env-agnostically;
 * mainnet `{1→eth-mainnet, 8453→base-mainnet}` is unchanged.
 */
object AlchemyNetworks {
    fun of(chainId: Long): String? =
        com.tneff.cyppie.evm.network.NetworkProfiles.chainProfile(chainId)?.alchemySlug

    fun chainId(slug: String): Long? =
        com.tneff.cyppie.evm.network.NetworkProfiles.all
            .firstNotNullOfOrNull { p -> p.chains.firstOrNull { it.alchemySlug == slug }?.chainId }

    /**
     * The chains the client builds RPC/NFT clients for **on mainnet** (the default). Under runtime env-switching
     * the app shell iterates `activeNetworkStore.profile.chainIds` instead — this stays mainnet so behavior is
     * unchanged until the shell wires the active env (PRD-09 Dev-1).
     */
    val supportedChainIds: List<Long> = com.tneff.cyppie.evm.network.NetworkProfiles.MAINNET.chainIds
}

/**
 * Client-side routing for the KAN-112 key-proxy (ADR-0021). All Alchemy/RPC traffic goes to the
 * `:server` proxy ([proxyBaseUrl], e.g. `http://localhost:8080` in dev), which injects the API key
 * server-side — so **no key ships in the client binary**. These builders produce exactly the base URLs
 * the existing Alchemy clients expect, but pointed at the proxy instead of `*.g.alchemy.com/{key}`.
 *
 * If the proxy is unreachable (or answers `503` because its key is unconfigured), the clients already
 * map that to [RpcException.AllProvidersFailed] → the app degrades cleanly (FR-4); no key, no leak.
 */
data class AlchemyProxyConfig(val proxyBaseUrl: String) {
    private val base: String = proxyBaseUrl.trimEnd('/')

    /** Alchemy Data API prefix (`AlchemyDataClient` appends `/assets/tokens/by-address`). */
    fun dataBaseUrl(): String = "$base/alchemy/data/v1"

    /** Alchemy Prices API prefix (`AlchemyPriceClient` appends `/tokens/by-address` · `/tokens/historical`). */
    fun pricesBaseUrl(): String = "$base/alchemy/prices/v1"

    /** Alchemy NFT API v3 prefix for [chainId] (`AlchemyNftClient` appends `/getNFTsForOwner`). */
    fun nftBaseUrl(chainId: Long): String = "$base/alchemy/nft/v3/${network(chainId)}"

    /** JSON-RPC endpoint for [chainId] (`AlchemyTransfersClient` / `EvmJsonRpcClient` POST here). */
    fun rpcUrl(chainId: Long): String = "$base/alchemy/rpc/v2/${network(chainId)}"

    /** CoinGecko v3 prefix (KAN-131) — `CoinGeckoMarketClient` appends `/simple/...` · `/coins/...`. The
     *  proxy injects the dedicated CoinGecko key server-side; unset key → 503 → market degrades (FR-4). */
    fun coinGeckoBaseUrl(): String = "$base/coingecko/v3"

    private fun network(chainId: Long): String =
        AlchemyNetworks.of(chainId) ?: error("Unsupported chainId for Alchemy proxy: $chainId")
}
