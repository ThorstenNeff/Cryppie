package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * [NftReadClient] over the Alchemy NFT API v3 (`getNFTsForOwner`). [baseUrl] is the chain-scoped,
 * key-bearing prefix `https://{network}.g.alchemy.com/nft/v3/{apiKey}` (network = `eth-mainnet` /
 * `base-mainnet`) — built from build-config and **never committed** (PRD-02 §9). Retry/timeout/429
 * handling comes from the shared Ktor client ([defaultRpcHttpClient]); request/transport failures map
 * to [RpcException]. Single-provider in the MVP, so [degraded] stays false (the field is reserved for
 * a later failover provider, FR-4).
 */
class AlchemyNftClient(
    private val chainId: Long,
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultRpcHttpClient(),
) : NftReadClient {

    override val degraded: Boolean = false

    override suspend fun nftsForOwner(
        owner: EvmAddress,
        pageKey: String?,
        pageSize: Int,
        excludeSpam: Boolean,
    ): NftPage {
        val response = try {
            httpClient.get("$baseUrl/getNFTsForOwner") {
                parameter("owner", owner.value)
                parameter("pageSize", pageSize.coerceIn(1, 100))
                parameter("withMetadata", true)
                if (pageKey != null) parameter("pageKey", pageKey)
                if (excludeSpam) parameter("excludeFilters[]", "SPAM")
            }
        } catch (e: RpcException) {
            throw e
        } catch (e: Throwable) {
            throw RpcException.AllProvidersFailed("Alchemy NFT request failed", e)
        }
        if (!response.status.isSuccess()) {
            throw RpcException.AllProvidersFailed("Alchemy NFT HTTP ${response.status.value}")
        }
        val dto = try {
            response.body<AlchemyNftResponse>()
        } catch (e: Throwable) {
            throw RpcException.Decoding("Alchemy NFT response could not be parsed")
        }
        return NftPage(
            items = dto.ownedNfts.mapNotNull { it.toNftItem(chainId) },
            nextPageKey = dto.pageKey,
            totalCount = dto.totalCount,
            degraded = degraded,
        )
    }
}

private fun AlchemyOwnedNft.toNftItem(chainId: Long): NftItem? {
    val contractAddress = contract?.address
        ?.let { Hex.decodeOrNull(it) }
        ?.takeIf { it.size == 20 }
        ?.let { EvmAddress.fromBytes(it) }
        ?: return null // unparseable contract address → drop the item rather than crash
    return NftItem(
        chainId = chainId,
        contract = contractAddress,
        tokenId = tokenId ?: "0",
        type = nftType(tokenType ?: contract.tokenType),
        name = name,
        collectionName = collection?.name ?: contract.name,
        imageUrl = image?.cachedUrl ?: image?.thumbnailUrl, // prefer Alchemy-cached (privacy, §2)
        balance = balance?.toLongOrNull()?.let { Quantity.of(it) },
        isSpam = contract.isSpam ?: false,
    )
}

private fun nftType(raw: String?): NftType = when (raw?.uppercase()) {
    "ERC721" -> NftType.ERC721
    "ERC1155" -> NftType.ERC1155
    else -> NftType.UNKNOWN
}

@Serializable
private data class AlchemyNftResponse(
    val ownedNfts: List<AlchemyOwnedNft> = emptyList(),
    val pageKey: String? = null,
    val totalCount: Int? = null,
)

@Serializable
private data class AlchemyOwnedNft(
    val contract: AlchemyContract? = null,
    val tokenId: String? = null,
    val tokenType: String? = null,
    val name: String? = null,
    val image: AlchemyImage? = null,
    val balance: String? = null,
    val collection: AlchemyCollection? = null,
)

@Serializable
private data class AlchemyContract(
    val address: String? = null,
    val name: String? = null,
    val tokenType: String? = null,
    val isSpam: Boolean? = null,
)

@Serializable
private data class AlchemyImage(
    val cachedUrl: String? = null,
    val thumbnailUrl: String? = null,
    val originalUrl: String? = null,
)

@Serializable
private data class AlchemyCollection(val name: String? = null)
