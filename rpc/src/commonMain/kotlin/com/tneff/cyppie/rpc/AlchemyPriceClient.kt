package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** A raw fiat price for a token from the Alchemy Prices API (decimal kept as a string for precision). */
data class RawTokenPrice(
    val chainId: Long,
    val contract: EvmAddress,
    val priceDecimal: String,
    val currency: String,
    val lastUpdatedIso: String?,
)

/**
 * Alchemy **Prices API** client (`tokens/by-address`) — current fiat prices by contract address per
 * network (ADR-0010 REST, web-capable). [baseUrl] = `https://api.g.alchemy.com/prices/v1/{apiKey}`
 * (build-config, never committed). The `value` decimal is returned as a **string** (precision; the
 * `PriceSource` valuation layer parses it big-int-disciplined). Errors → [RpcException].
 */
class AlchemyPriceClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultRpcHttpClient(),
) {
    val degraded: Boolean = false

    /** Current prices for [tokens] (chainId → contract) in [vs] fiat. Tokens with no price are omitted. */
    suspend fun pricesByAddress(tokens: List<Pair<Long, EvmAddress>>, vs: String): List<RawTokenPrice> {
        if (tokens.isEmpty()) return emptyList()
        val request = PricesByAddressRequest(
            addresses = tokens.mapNotNull { (chainId, contract) ->
                alchemyNetwork(chainId)?.let { PriceAddress(it, contract.value) }
            },
        )
        if (request.addresses.isEmpty()) return emptyList()
        val response = try {
            httpClient.post("$baseUrl/tokens/by-address") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: RpcException) {
            throw e
        } catch (e: Throwable) {
            throw RpcException.AllProvidersFailed("Alchemy Prices request failed", e)
        }
        if (!response.status.isSuccess()) {
            throw RpcException.AllProvidersFailed("Alchemy Prices HTTP ${response.status.value}")
        }
        val dto = try {
            response.body<PricesByAddressResponse>()
        } catch (e: Throwable) {
            throw RpcException.Decoding("Alchemy Prices response could not be parsed")
        }
        return dto.data.mapNotNull { it.toRawPrice(vs) }
    }

    private fun PriceResult.toRawPrice(vs: String): RawTokenPrice? {
        if (error != null) return null
        val chainId = networkChainId(network) ?: return null
        val contract = com.tneff.cyppie.evm.Hex.decodeOrNull(address.orEmpty())
            ?.takeIf { it.size == 20 }?.let { EvmAddress.fromBytes(it) } ?: return null
        // Only the requested fiat — never a wrong-currency price (L1): a missing currency → no price.
        val price = prices.firstOrNull { it.currency.equals(vs, ignoreCase = true) } ?: return null
        val value = price.value ?: return null
        return RawTokenPrice(chainId, contract, value, price.currency, price.lastUpdatedAt)
    }

    private companion object {
        fun alchemyNetwork(chainId: Long): String? = when (chainId) {
            1L -> "eth-mainnet"
            8453L -> "base-mainnet"
            else -> null
        }

        fun networkChainId(network: String?): Long? = when (network) {
            "eth-mainnet" -> 1L
            "base-mainnet" -> 8453L
            else -> null
        }
    }
}

@Serializable
private data class PricesByAddressRequest(val addresses: List<PriceAddress>)

@Serializable
private data class PriceAddress(val network: String, val address: String)

@Serializable
private data class PricesByAddressResponse(val data: List<PriceResult> = emptyList())

@Serializable
private data class PriceResult(
    val network: String? = null,
    val address: String? = null,
    val prices: List<AlchemyPrice> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class AlchemyPrice(
    val currency: String = "usd",
    val value: String? = null,
    val lastUpdatedAt: String? = null,
)
