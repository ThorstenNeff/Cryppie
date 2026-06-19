package com.tneff.cyppie.market

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * CoinGecko adapter (PRD-04). [baseUrl] points at the **key-proxy** route (`…/coingecko/v3`), never at
 * CoinGecko directly — the Pro key is injected **server-side** (FR-6), so no key ships in the client.
 *
 * Decimals are read as **strings via `JsonPrimitive.content`** (precision, FR-6) — CoinGecko returns JSON
 * numbers and a `Double` round-trip would lose precision; the literal token text is preserved instead.
 * Timestamps arrive in **milliseconds** and are normalized to epoch **seconds**.
 */
class CoinGeckoMarketClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultMarketHttpClient(),
) {
    /** `/simple/token_price/{platform}` — spot by contract; returns lowercase-contract → [SpotPrice]. */
    suspend fun tokenSpotPrices(platform: String, contractsLower: List<String>, vs: String): Map<String, SpotPrice> {
        if (contractsLower.isEmpty()) return emptyMap()
        val text = getText("$baseUrl/simple/token_price/$platform") {
            parameter("contract_addresses", contractsLower.joinToString(","))
            parameter("vs_currencies", vs)
            parameter("include_24hr_change", "true")
            parameter("include_last_updated_at", "true")
        }
        val obj = parse(text).jsonObject
        return buildMap {
            for ((contract, v) in obj) {
                val row = v.jsonObject
                val price = row[vs]?.jsonPrimitive?.contentOrNull ?: continue
                put(
                    contract.lowercase(),
                    SpotPrice(
                        priceDecimal = price,
                        vs = vs,
                        change24hPct = row["${vs}_24h_change"]?.jsonPrimitive?.contentOrNull,
                        lastUpdatedEpochSeconds = row["last_updated_at"]?.jsonPrimitive?.longOrNull,
                    ),
                )
            }
        }
    }

    /** `/simple/price` — spot by **coin id** (for native coins that have no contract); id → [SpotPrice]. */
    suspend fun coinSpotPrices(ids: List<String>, vs: String): Map<String, SpotPrice> {
        if (ids.isEmpty()) return emptyMap()
        val text = getText("$baseUrl/simple/price") {
            parameter("ids", ids.joinToString(","))
            parameter("vs_currencies", vs)
            parameter("include_24hr_change", "true")
            parameter("include_last_updated_at", "true")
        }
        val obj = parse(text).jsonObject
        return buildMap {
            for ((id, v) in obj) {
                val row = v.jsonObject
                val price = row[vs]?.jsonPrimitive?.contentOrNull ?: continue
                put(
                    id,
                    SpotPrice(
                        priceDecimal = price,
                        vs = vs,
                        change24hPct = row["${vs}_24h_change"]?.jsonPrimitive?.contentOrNull,
                        lastUpdatedEpochSeconds = row["last_updated_at"]?.jsonPrimitive?.longOrNull,
                    ),
                )
            }
        }
    }

    /** `/coins/markets` — market stats (cap / circulating supply / 24h volume) by coin id; id → [MarketStats]. */
    suspend fun coinsMarkets(ids: List<String>, vs: String): Map<String, MarketStats> {
        if (ids.isEmpty()) return emptyMap()
        val text = getText("$baseUrl/coins/markets") {
            parameter("vs_currency", vs)
            parameter("ids", ids.joinToString(","))
        }
        return buildMap {
            for (row in parse(text).jsonArray) {
                val o = row.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: continue
                put(
                    id,
                    MarketStats(
                        marketCap = o["market_cap"]?.jsonPrimitive?.contentOrNull,
                        circulatingSupply = o["circulating_supply"]?.jsonPrimitive?.contentOrNull,
                        volume24h = o["total_volume"]?.jsonPrimitive?.contentOrNull,
                        vs = vs,
                    ),
                )
            }
        }
    }

    /** `/coins/{platform}/contract/{contract}/market_chart/range` — historical price points by contract. */
    suspend fun contractMarketChartRange(
        platform: String,
        contractLower: String,
        vs: String,
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
    ): List<PricePoint> {
        val text = getText("$baseUrl/coins/$platform/contract/$contractLower/market_chart/range") {
            parameter("vs_currency", vs)
            parameter("from", fromEpochSeconds.toString())
            parameter("to", toEpochSeconds.toString())
        }
        val prices = parse(text).jsonObject["prices"]?.jsonArray ?: return emptyList()
        return prices.mapNotNull { pair ->
            val arr = pair.jsonArray
            val ts = arr.getOrNull(0)?.jsonPrimitive?.epochSecondsFromMillis() ?: return@mapNotNull null
            val price = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            PricePoint(ts, price)
        }
    }

    /** `/coins/{coinId}/ohlc` — OHLC candles by coin id (no volume). [days] e.g. "1","7","30","max". */
    suspend fun coinOhlc(coinId: String, vs: String, days: String): List<Candle> {
        val text = getText("$baseUrl/coins/$coinId/ohlc") {
            parameter("vs_currency", vs)
            parameter("days", days)
        }
        return parse(text).jsonArray.mapNotNull { row ->
            val a = row.jsonArray
            val ts = a.getOrNull(0)?.jsonPrimitive?.epochSecondsFromMillis() ?: return@mapNotNull null
            Candle(
                openEpochSeconds = ts,
                open = a.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                high = a.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                low = a.getOrNull(3)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                close = a.getOrNull(4)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                volume = null, // CoinGecko /ohlc carries no volume
            )
        }
    }

    private suspend fun getText(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String {
        val response: HttpResponse = httpClient.get(url) { block() }
        if (!response.status.isSuccess()) {
            throw MarketException.Upstream("CoinGecko ${response.status} for $url")
        }
        return response.bodyAsText()
    }

    private fun parse(text: String) = try {
        clientJson.parseToJsonElement(text)
    } catch (e: Exception) {
        throw MarketException.Upstream("Malformed CoinGecko response: ${e.message}")
    }

    private companion object {
        val clientJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** CoinGecko timestamps are epoch **milliseconds**; normalize to seconds (keeps Long, no float). */
internal fun kotlinx.serialization.json.JsonPrimitive.epochSecondsFromMillis(): Long? =
    (longOrNull ?: doubleOrNull?.toLong())?.let { it / 1000 }
