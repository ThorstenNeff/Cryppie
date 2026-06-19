package com.tneff.cyppie

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.tneff.cyppie.KeyProxy")

fun main() {
    val config = ProxyConfig.fromEnvironment()
    if (!config.keyConfigured) {
        log.warn(
            "ALCHEMY key not configured (set gradle property 'alchemyApiKey' in ~/.gradle/gradle.properties " +
                "or env ALCHEMY_API_KEY). Proxy endpoints will answer 503 until a key is present.",
        )
    }
    // ADR-0022 D: in prod, bind 127.0.0.1 (PROXY_BIND_HOST) so only the local reverse proxy reaches Ktor.
    embeddedServer(Netty, port = config.port, host = config.bindHost, module = { module(config) }).start(wait = true)
}

/**
 * Wires the KAN-112 key-proxy. [config], the forwarding [client] and the [rateLimiter] are injectable
 * so a test can supply a `MockEngine` client + a fixed/keyed config (ADR-0021).
 */
fun Application.module(
    config: ProxyConfig = ProxyConfig.fromEnvironment(),
    client: HttpClient = defaultProxyClient(),
    rateLimiter: FixedWindowRateLimiter = FixedWindowRateLimiter(config.rateLimitPerMinute),
) {
    installKeyProxy(config, client, rateLimiter)
}

/** CIO-backed forwarding client. `expectSuccess = false` so upstream error statuses relay verbatim. */
internal fun defaultProxyClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
}
