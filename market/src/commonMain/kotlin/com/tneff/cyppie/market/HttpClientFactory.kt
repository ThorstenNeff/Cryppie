package com.tneff.cyppie.market

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Lenient JSON for market upstreams (CoinGecko/Binance add fields we don't model). */
internal val marketJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

/** Creates an [HttpClient] on the target's Ktor engine (OkHttp/Darwin/CIO/Js), applying [block]. */
internal expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * Production market-data client: platform engine + JSON content negotiation + timeouts + retry (ADR-0010).
 * Retries the same endpoint on 429 / 5xx + transport errors with exponential backoff + jitter; provider
 * failover (CoinGecko↔Binance) is the full PRD-08 service's job, not the client's.
 */
internal fun defaultMarketHttpClient(): HttpClient = platformHttpClient {
    install(ContentNegotiation) { json(marketJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    install(HttpRequestRetry) {
        retryIf(maxRetries = 3) { _, response -> response.status.value == 429 || response.status.value in 500..599 }
        retryOnException(maxRetries = 3, retryOnTimeout = true)
        exponentialDelay(randomizationMs = 1_000)
    }
}
