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
) {
    val keyConfigured: Boolean get() = !alchemyApiKey.isNullOrBlank()

    companion object {
        val DEFAULT_NETWORKS: Set<String> = setOf("eth-mainnet", "base-mainnet")
        const val DEFAULT_RATE_LIMIT: Int = 120

        /** Reads the key from the system property (build-bridged gradle prop) then the env var. */
        fun fromEnvironment(): ProxyConfig {
            val key = System.getProperty("alchemyApiKey")?.takeIf { it.isNotBlank() }
                ?: System.getenv("ALCHEMY_API_KEY")?.takeIf { it.isNotBlank() }
            return ProxyConfig(alchemyApiKey = key)
        }
    }
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
