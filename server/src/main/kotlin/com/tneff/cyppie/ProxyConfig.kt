package com.tneff.cyppie

/**
 * KAN-112 key-proxy configuration (ADR-0021). The Alchemy API key lives **only** here, read at startup
 * from the JVM system property `alchemyApiKey` (the build bridges it from the machine-wide
 * `~/.gradle/gradle.properties`, never committed) or the `ALCHEMY_API_KEY` env var (deploy). When no
 * key is configured the server still boots, but proxy calls answer `503` so a client degrades cleanly
 * (FR-4) instead of leaking a misconfiguration as a hard failure.
 */
data class ProxyConfig(
    val alchemyApiKey: String?,
    /** Upstream networks the proxy is willing to reach — keeps it from being an open relay (abuse guard). */
    val allowedNetworks: Set<String> = DEFAULT_NETWORKS,
    /** Per-client request budget per minute (abuse guard). */
    val rateLimitPerMinute: Int = DEFAULT_RATE_LIMIT,
    /**
     * Number of trusted reverse-proxy hops in front of this server (ADR-0022 B). `0` = the server is
     * reached directly (dev), so the rate-limiter keys on the socket peer. `>0` = behind a trusted
     * reverse proxy (Caddy/nginx), so the real client IP is the Nth-from-rightmost `X-Forwarded-For`
     * entry — otherwise every request looks like the proxy and the limit collapses globally.
     */
    val trustedProxyHops: Int = 0,
    /** Max request body the proxy will forward; larger → `413` (abuse guard, ADR-0022). */
    val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
    /** JSON-RPC methods allowed on `/alchemy/rpc` — wallet reads + broadcast only; others → `403`. */
    val allowedRpcMethods: Set<String> = DEFAULT_RPC_METHODS,
    /** Bind host: `0.0.0.0` (dev/container) or `127.0.0.1` (prod behind the reverse proxy, ADR-0022 D). */
    val bindHost: String = "0.0.0.0",
    /** Bind port (the reverse proxy fronts it; not public in prod). */
    val port: Int = 8080,
) {
    val keyConfigured: Boolean get() = !alchemyApiKey.isNullOrBlank()

    companion object {
        val DEFAULT_NETWORKS: Set<String> = setOf("eth-mainnet", "base-mainnet")
        const val DEFAULT_RATE_LIMIT: Int = 120
        const val DEFAULT_MAX_BODY_BYTES: Long = 256 * 1024 // 256 KiB — generous for batched JSON-RPC

        /** Read-only + broadcast JSON-RPC the wallet actually uses (read/broadcast-only proxy, ADR-0022). */
        val DEFAULT_RPC_METHODS: Set<String> = setOf(
            "eth_blockNumber", "eth_chainId", "eth_getBalance", "eth_call", "eth_getCode",
            "eth_getTransactionCount", "eth_gasPrice", "eth_maxPriorityFeePerGas", "eth_feeHistory",
            "eth_estimateGas", "eth_getBlockByNumber", "eth_getTransactionByHash", "eth_getTransactionReceipt",
            "eth_getLogs", "eth_sendRawTransaction", "alchemy_getAssetTransfers", "alchemy_getTokenBalances",
        )

        /** Reads the key + prod knobs from system properties / env vars (deploy sets these; never the repo). */
        fun fromEnvironment(): ProxyConfig {
            val key = System.getProperty("alchemyApiKey")?.takeIf { it.isNotBlank() }
                ?: System.getenv("ALCHEMY_API_KEY")?.takeIf { it.isNotBlank() }
            fun env(name: String): String? = System.getProperty(name) ?: System.getenv(name)
            return ProxyConfig(
                alchemyApiKey = key,
                rateLimitPerMinute = env("PROXY_RATE_LIMIT")?.toIntOrNull() ?: DEFAULT_RATE_LIMIT,
                trustedProxyHops = env("PROXY_TRUSTED_HOPS")?.toIntOrNull() ?: 0,
                maxBodyBytes = env("PROXY_MAX_BODY_BYTES")?.toLongOrNull() ?: DEFAULT_MAX_BODY_BYTES,
                bindHost = env("PROXY_BIND_HOST") ?: "0.0.0.0",
                port = env("server.port")?.toIntOrNull() ?: env("PORT")?.toIntOrNull() ?: 8080,
            )
        }
    }
}

/**
 * Extracts the real client IP for rate-limiting (ADR-0022 B). With [trustedHops] = 0 we trust only the
 * socket peer ([remoteHost]). With N trusted reverse-proxy hops, the client is the entry N-from-the-end
 * of `X-Forwarded-For` (each trusted hop appends one); anything beyond what the proxy could have set is
 * attacker-controlled and ignored. Falls back to [remoteHost] if the header is missing/short.
 */
fun realClientIp(remoteHost: String, xForwardedFor: String?, trustedHops: Int): String {
    if (trustedHops <= 0 || xForwardedFor.isNullOrBlank()) return remoteHost
    val hops = xForwardedFor.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    // The rightmost entry is the closest trusted proxy; step back `trustedHops` to reach the real client.
    return hops.getOrNull(hops.size - trustedHops) ?: hops.firstOrNull() ?: remoteHost
}

/**
 * Pure upstream-URL builders for the Alchemy products the proxy fronts. The key is injected here,
 * server-side; the client only ever sees the proxy path (`/alchemy/...`). Kept as pure functions so
 * the key-injection contract is unit-testable without standing up the server.
 */
object AlchemyUpstream {
    fun data(key: String, tail: String): String = "https://api.g.alchemy.com/data/v1/$key/$tail"
    fun prices(key: String, tail: String): String = "https://api.g.alchemy.com/prices/v1/$key/$tail"
    fun nft(network: String, key: String, tail: String): String =
        "https://$network.g.alchemy.com/nft/v3/$key/$tail"
    fun rpc(network: String, key: String): String = "https://$network.g.alchemy.com/v2/$key"
}

/**
 * Upstream-URL builder for CoinGecko (PRD-04 market data). Unlike Alchemy (key in path), CoinGecko takes
 * the Pro key as the `x_cg_pro_api_key` query param — injected here **server-side** so it never ships in
 * the client; the proxy then appends the client's own query (`vs_currency`/`from`/`to`/…) faithfully.
 * Pure function → unit-testable. NOTE: today this reuses [ProxyConfig.alchemyApiKey] as the generic
 * upstream key slot; split into a dedicated `coinGeckoApiKey` when the real Pro key is provisioned.
 */
object CoinGeckoUpstream {
    fun url(key: String, tail: String): String = "https://pro-api.coingecko.com/api/v3/$tail?x_cg_pro_api_key=$key"
}
