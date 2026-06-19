package com.tneff.cyppie

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Installs the KAN-112 key-proxy routes (ADR-0021). Every Alchemy/RPC request the app makes is routed
 * here as `/alchemy/...`; the proxy injects the server-side key and forwards to the real upstream, so
 * **no key ships in the client binary**. Pass-through is method/query/body faithful and returns the
 * upstream status + body verbatim. Guards: per-client rate limit, a network allow-list (no open relay),
 * and a `503` when the key is unconfigured (clean client-side degrade, FR-4).
 */
fun Application.installKeyProxy(
    config: ProxyConfig,
    client: HttpClient,
    rateLimiter: FixedWindowRateLimiter,
) {
    routing {
        get("/") { call.respondText("Cyppie key-proxy") }
        get("/healthz") {
            call.respondText(
                """{"status":"ok","keyConfigured":${config.keyConfigured}}""",
                ContentType.Application.Json,
            )
        }

        // Alchemy Data API (multi-chain balances/metadata) — network is in the request body.
        // KAN-115: only the exact sub-path the client uses is forwarded (least-privilege tail allow-list).
        route("/alchemy/data/v1/{tail...}") {
            handle {
                proxy(config, client, rateLimiter) { key ->
                    tailPath().takeIf { it in ALLOWED_DATA_TAILS }?.let { AlchemyUpstream.data(key, it) }
                }
            }
        }
        // Alchemy Prices API (current + historical) — network is in the request body.
        route("/alchemy/prices/v1/{tail...}") {
            handle {
                proxy(config, client, rateLimiter) { key ->
                    tailPath().takeIf { it in ALLOWED_PRICES_TAILS }?.let { AlchemyUpstream.prices(key, it) }
                }
            }
        }
        // Alchemy NFT API v3 — network is path-scoped (allow-list) and the tail is allow-listed (KAN-115).
        route("/alchemy/nft/v3/{network}/{tail...}") {
            handle {
                proxy(config, client, rateLimiter) { key ->
                    val net = allowedNetwork(config)
                    val tail = tailPath().takeIf { it in ALLOWED_NFT_TAILS }
                    if (net != null && tail != null) AlchemyUpstream.nft(net, key, tail) else null
                }
            }
        }
        // JSON-RPC (eth_* + alchemy_getAssetTransfers) — network is path-scoped + method allow-listed.
        route("/alchemy/rpc/v2/{network}") {
            handle {
                proxy(config, client, rateLimiter, enforceRpcMethods = true) { key ->
                    allowedNetwork(config)?.let { AlchemyUpstream.rpc(it, key) }
                }
            }
        }
        // CoinGecko market data (PRD-04) — Pro key injected server-side. The tail is allow-listed by
        // endpoint *shape* (variable platform/contract/id segments), so the key only ever reaches the
        // handful of price/candle endpoints the app uses — never an arbitrary CoinGecko path.
        route("/coingecko/v3/{tail...}") {
            handle {
                proxy(config, client, rateLimiter) { key ->
                    tailPath().takeIf { isAllowedCoinGeckoTail(it) }?.let { CoinGeckoUpstream.url(key, it) }
                }
            }
        }
    }
}

// KAN — CoinGecko least-privilege allow-list. Unlike Alchemy's fixed tails, CoinGecko paths carry
// variable platform/contract/coin-id segments, so we match by *structure* (and bound the platform to the
// chains we support) rather than exact strings. Only these endpoint shapes are forwarded.
private val COINGECKO_PLATFORMS: Set<String> = setOf("ethereum", "base")
private val COINGECKO_ID = Regex("[a-z0-9-]{1,64}")
private val COINGECKO_HEX_ADDRESS = Regex("0x[0-9a-fA-F]{40}")

internal fun isAllowedCoinGeckoTail(tail: String): Boolean {
    val s = tail.split("/")
    return when {
        // simple/token_price/{platform} — spot by contract
        s.size == 3 && s[0] == "simple" && s[1] == "token_price" && s[2] in COINGECKO_PLATFORMS -> true
        // simple/price — spot by coin id
        s.size == 2 && s[0] == "simple" && s[1] == "price" -> true
        // coins/{platform}/contract/{address}/market_chart[/range] — history by contract
        (s.size == 5 || s.size == 6) && s[0] == "coins" && s[1] in COINGECKO_PLATFORMS && s[2] == "contract" &&
            COINGECKO_HEX_ADDRESS.matches(s[3]) && s[4] == "market_chart" && (s.size == 5 || s[5] == "range") -> true
        // coins/{id}/ohlc — candles by coin id
        s.size == 3 && s[0] == "coins" && COINGECKO_ID.matches(s[1]) && s[2] == "ohlc" -> true
        else -> false
    }
}

// KAN-115 (least-privilege): the exact upstream sub-paths the wallet's Alchemy clients call. Any other
// tail is rejected (404) so the server-side key can only ever reach these endpoints — not an arbitrary
// path the key happens to be entitled to. Extend deliberately as new client calls are added.
private val ALLOWED_DATA_TAILS: Set<String> = setOf("assets/tokens/by-address")
private val ALLOWED_PRICES_TAILS: Set<String> = setOf("tokens/by-address", "tokens/historical")
private val ALLOWED_NFT_TAILS: Set<String> = setOf("getNFTsForOwner")

/** Tail path segments captured by `{tail...}`, rejoined (empty when none). */
private fun RoutingContext.tailPath(): String =
    call.parameters.getAll("tail")?.joinToString("/").orEmpty()

/** The `{network}` segment, but only if it is on the proxy's allow-list (else null → 404). */
private fun RoutingContext.allowedNetwork(config: ProxyConfig): String? =
    call.parameters["network"]?.takeIf { it in config.allowedNetworks }

/**
 * True iff every JSON-RPC call in [body] (single object or batch array) uses an [allowed] method.
 * Fail-closed: unparseable or method-less requests are rejected (the proxy is read/broadcast-only).
 */
private val rpcParseJson = Json { ignoreUnknownKeys = true; isLenient = true }
private fun rpcMethodsAllowed(body: ByteArray, allowed: Set<String>): Boolean {
    val element = try {
        rpcParseJson.parseToJsonElement(body.decodeToString())
    } catch (e: Exception) {
        return false
    }
    val requests = when (element) {
        is JsonArray -> element
        is JsonObject -> listOf(element)
        else -> return false
    }
    if (requests.isEmpty()) return false
    return requests.all { req ->
        val method = (req as? JsonObject)?.get("method")?.let { (it as? JsonPrimitive)?.contentOrNull }
        method != null && method in allowed
    }
}

/**
 * Core forward: rate-limit → key gate → build upstream (allow-list) → faithfully relay method/query/
 * body → return upstream status + body. [buildUpstreamUrl] returns null when the target is rejected
 * (e.g. a network outside the allow-list).
 */
private suspend fun RoutingContext.proxy(
    config: ProxyConfig,
    client: HttpClient,
    rateLimiter: FixedWindowRateLimiter,
    enforceRpcMethods: Boolean = false,
    buildUpstreamUrl: RoutingContext.(key: String) -> String?,
) {
    // ADR-0022 B: behind a reverse proxy the socket peer is the proxy, so rate-limit on the real client.
    val clientId = realClientIp(
        remoteHost = call.request.local.remoteHost,
        xForwardedFor = call.request.headers["X-Forwarded-For"],
        trustedHops = config.trustedProxyHops,
    )
    if (!rateLimiter.allow(clientId)) {
        call.respondText("Rate limit exceeded", status = HttpStatusCode.TooManyRequests)
        return
    }
    val key = config.alchemyApiKey
    if (key.isNullOrBlank()) {
        call.respondText("Upstream key not configured", status = HttpStatusCode.ServiceUnavailable)
        return
    }
    val upstreamUrl = buildUpstreamUrl(key)
    if (upstreamUrl == null) {
        call.respondText("Unsupported upstream", status = HttpStatusCode.NotFound)
        return
    }

    val method = call.request.httpMethod
    // ADR-0022: cap request bodies (the reverse proxy enforces a hard edge cap too; this is defence-in-depth).
    val declaredLength = call.request.contentLength()
    if (declaredLength != null && declaredLength > config.maxBodyBytes) {
        call.respondText("Request body too large", status = HttpStatusCode.PayloadTooLarge)
        return
    }
    val requestBody: ByteArray? =
        if (method != HttpMethod.Get && method != HttpMethod.Head) call.receive<ByteArray>() else null
    if (requestBody != null && requestBody.size > config.maxBodyBytes) {
        call.respondText("Request body too large", status = HttpStatusCode.PayloadTooLarge)
        return
    }
    // JSON-RPC method allow-list (read/broadcast-only proxy) — reject anything else before it reaches upstream.
    if (enforceRpcMethods && requestBody != null && !rpcMethodsAllowed(requestBody, config.allowedRpcMethods)) {
        call.respondText("JSON-RPC method not allowed", status = HttpStatusCode.Forbidden)
        return
    }

    val response: HttpResponse = try {
        client.request(upstreamUrl) {
            this.method = method
            url { parameters.appendAll(call.request.queryParameters) }
            if (requestBody != null) {
                setBody(requestBody)
                contentType(ContentType.Application.Json)
            }
        }
    } catch (e: Throwable) {
        call.respondText("Upstream request failed", status = HttpStatusCode.BadGateway)
        return
    }

    val responseType = response.headers[HttpHeaders.ContentType]
        ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
        ?: ContentType.Application.Json
    call.respondBytes(response.body<ByteArray>(), responseType, response.status)
}
