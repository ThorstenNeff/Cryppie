package com.tneff.cyppie.rpc

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Lenient JSON for JSON-RPC (nodes add fields we don't model). */
internal val rpcJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

/** Creates an [HttpClient] on the target's Ktor engine (OkHttp/Darwin/CIO/Js), applying [block]. */
internal expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * Production RPC client: platform engine + JSON content negotiation + timeouts + retry (ADR-0010).
 *
 * Per-provider resilience: retry the *same* endpoint on 429 / 5xx and on transport exceptions, with
 * exponential backoff + jitter (respecting `Retry-After`). Cross-provider failover (Alchemy→Infura)
 * is handled one level up in [EvmJsonRpcClient]; the two layers compose.
 */
internal fun defaultRpcHttpClient(): HttpClient = platformHttpClient {
    install(ContentNegotiation) { json(rpcJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    install(HttpRequestRetry) {
        retryIf(maxRetries = 3) { _, response -> response.status.value == 429 || response.status.value in 500..599 }
        retryOnException(maxRetries = 3, retryOnTimeout = true)
        exponentialDelay(randomizationMs = 1_000) // jitter; respects Retry-After by default
    }
}
