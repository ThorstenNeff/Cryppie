package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** A raw ERC-20 holding from the Alchemy Data API (PRD-03 P1). Native balance comes from L3 separately. */
data class RawTokenHolding(
    val holder: EvmAddress,
    val chainId: Long,
    val contract: EvmAddress,
    val balance: Quantity,
    val symbol: String?,
    val decimals: Int?,
)

/**
 * Alchemy **Data API** client (`assets/tokens/by-address`) — multi-chain ERC-20 balances + metadata in
 * one call per holder (ADR-0010 REST-beside-JSON-RPC, web-capable). [baseUrl] is the key-bearing prefix
 * `https://api.g.alchemy.com/data/v1/{apiKey}` (build-config, never committed). Errors → [RpcException].
 */
class AlchemyDataClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultRpcHttpClient(),
) {
    val degraded: Boolean = false

    /** ERC-20 holdings for each of [holders] across [chainIds] (Ethereum / Base). Native is L3's job. */
    suspend fun tokenHoldings(holders: List<EvmAddress>, chainIds: List<Long>): List<RawTokenHolding> {
        val networks = chainIds.mapNotNull { alchemyNetwork(it) }
        if (networks.isEmpty()) return emptyList()
        return holders.flatMap { holder -> tokensForHolder(holder, networks) }
    }

    private suspend fun tokensForHolder(holder: EvmAddress, networks: List<String>): List<RawTokenHolding> {
        // Follow `pageKey` to the end — token-rich wallets span multiple pages (M1; like AlchemyNftClient).
        val all = mutableListOf<RawTokenHolding>()
        var pageKey: String? = null
        var pages = 0
        do {
            val dto = fetchPage(holder, networks, pageKey)
            dto.data?.tokens.orEmpty().forEach { it.toHolding(holder)?.let(all::add) }
            pageKey = dto.data?.pageKey
        } while (pageKey != null && ++pages < MAX_PAGES)
        return all
    }

    private suspend fun fetchPage(holder: EvmAddress, networks: List<String>, pageKey: String?): TokensByAddressResponse {
        val response = try {
            httpClient.post("$baseUrl/assets/tokens/by-address") {
                contentType(ContentType.Application.Json)
                setBody(TokensByAddressRequest(listOf(AddressNetworks(holder.value, networks)), pageKey))
            }
        } catch (e: RpcException) {
            throw e
        } catch (e: Throwable) {
            throw RpcException.AllProvidersFailed("Alchemy Data request failed", e)
        }
        if (!response.status.isSuccess()) {
            throw RpcException.AllProvidersFailed("Alchemy Data HTTP ${response.status.value}")
        }
        return try {
            response.body()
        } catch (e: Throwable) {
            throw RpcException.Decoding("Alchemy Data response could not be parsed")
        }
    }

    private companion object {
        const val MAX_PAGES = 25 // safety cap on pagination

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

    private fun AlchemyToken.toHolding(holder: EvmAddress): RawTokenHolding? {
        val chainId = networkChainId(network) ?: return null
        val contract = tokenAddress?.let { Hex.decodeOrNull(it) }?.takeIf { it.size == 20 }
            ?.let { EvmAddress.fromBytes(it) } ?: return null
        val balance = tokenBalance?.let { runCatching { Quantity.ofHex(it) }.getOrNull() } ?: Quantity.ZERO
        return RawTokenHolding(holder, chainId, contract, balance, tokenMetadata?.symbol, tokenMetadata?.decimals)
    }
}

@Serializable
private data class TokensByAddressRequest(val addresses: List<AddressNetworks>, val pageKey: String? = null)

@Serializable
private data class AddressNetworks(val address: String, val networks: List<String>)

@Serializable
private data class TokensByAddressResponse(val data: TokensData? = null)

@Serializable
private data class TokensData(val tokens: List<AlchemyToken> = emptyList(), val pageKey: String? = null)

@Serializable
private data class AlchemyToken(
    val network: String? = null,
    val tokenAddress: String? = null,
    val tokenBalance: String? = null,
    val tokenMetadata: AlchemyTokenMetadata? = null,
)

@Serializable
private data class AlchemyTokenMetadata(
    val symbol: String? = null,
    val decimals: Int? = null,
    val name: String? = null,
)
