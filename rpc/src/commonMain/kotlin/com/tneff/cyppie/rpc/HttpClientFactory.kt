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

/** Production RPC client: platform engine + JSON content negotiation + timeouts + server-error retry (ADR-0010). */
internal fun defaultRpcHttpClient(): HttpClient = platformHttpClient {
    install(ContentNegotiation) { json(rpcJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 2)
        exponentialDelay()
    }
}
