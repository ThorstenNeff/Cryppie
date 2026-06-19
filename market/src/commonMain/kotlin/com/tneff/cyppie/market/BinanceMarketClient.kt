package com.tneff.cyppie.market

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Binance public-market-data adapter (PRD-04) — **keyless** klines for major pairs (good candle source,
 * generous limits). [baseUrl] defaults to the public API; it can be pointed at a proxy route instead for
 * uniform central rate-limiting (PRD-04 open Q4 — keyless, so no secret to hide either way).
 *
 * Binance returns OHLCV as **strings** already (precision-safe); open time is epoch **milliseconds**,
 * normalized to seconds. The `MarketAsset`→trading-pair mapping (e.g. ETH→`ETHUSDT`) is the caller's
 * (the policy is PRD-04 open Q3), so this client takes a [symbol] directly.
 */
class BinanceMarketClient(
    private val baseUrl: String = "https://api.binance.com",
    private val httpClient: HttpClient = defaultMarketHttpClient(),
) {
    /** `/api/v3/klines` — candles for [symbol] (e.g. "ETHUSDT") at [interval] within an optional window. */
    suspend fun klines(
        symbol: String,
        interval: CandleInterval,
        startEpochSeconds: Long? = null,
        endEpochSeconds: Long? = null,
        limit: Int? = null,
    ): List<Candle> {
        val response: HttpResponse = httpClient.get("$baseUrl/api/v3/klines") {
            parameter("symbol", symbol)
            parameter("interval", interval.toBinanceInterval())
            startEpochSeconds?.let { parameter("startTime", (it * 1000).toString()) }
            endEpochSeconds?.let { parameter("endTime", (it * 1000).toString()) }
            limit?.let { parameter("limit", it.toString()) }
        }
        if (!response.status.isSuccess()) {
            throw MarketException.Upstream("Binance ${response.status} for klines $symbol")
        }
        val arr = try {
            clientJson.parseToJsonElement(response.bodyAsText()).jsonArray
        } catch (e: Exception) {
            throw MarketException.Upstream("Malformed Binance response: ${e.message}")
        }
        // Each kline: [openTime(ms), "open", "high", "low", "close", "volume", closeTime, ...].
        return arr.mapNotNull { row ->
            val k = row.jsonArray
            val openMs = k.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            Candle(
                openEpochSeconds = openMs / 1000,
                open = k.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                high = k.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                low = k.getOrNull(3)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                close = k.getOrNull(4)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                volume = k.getOrNull(5)?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun CandleInterval.toBinanceInterval(): String = when (this) {
        CandleInterval.M1 -> "1m"
        CandleInterval.M5 -> "5m"
        CandleInterval.M15 -> "15m"
        CandleInterval.H1 -> "1h"
        CandleInterval.H4 -> "4h"
        CandleInterval.D1 -> "1d"
    }

    private companion object {
        val clientJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
