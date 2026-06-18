package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity

/** ERC-721 / ERC-1155 / unknown (never throw on an unexpected `tokenType`). */
enum class NftType { ERC721, ERC1155, UNKNOWN }

/**
 * A read-only NFT holding (NFT-READ-SPEC). [chainId] identifies the chain (the UI maps it to the
 * `EvmChain` enum, which lives in `:walletcore`; keeping `:rpc` chain-enum-free avoids a dependency
 * cycle). [imageUrl] is the **Alchemy-cached** URL (privacy — the device never fetches from arbitrary
 * NFT origins/IPFS). [balance] is set only for ERC-1155.
 */
data class NftItem(
    val chainId: Long,
    val contract: EvmAddress,
    val tokenId: String,
    val type: NftType,
    val name: String?,
    val collectionName: String?,
    val imageUrl: String?,
    val balance: Quantity?,
    val isSpam: Boolean,
)

/** One page of an owner's NFTs. [nextPageKey] null ⇒ last page. [degraded] mirrors FR-4. */
data class NftPage(
    val items: List<NftItem>,
    val nextPageKey: String?,
    val totalCount: Int?,
    val degraded: Boolean,
)

/**
 * Read-only NFT source for one chain (one instance per chain, like `rpcByChain`). Backed by the
 * Alchemy NFT API v3 `getNFTsForOwner` (ADR-0010 REST-beside-JSON-RPC; web-capable, `:evm`-only).
 */
interface NftReadClient {
    /** True when serving from a fallback provider (FR-4). */
    val degraded: Boolean

    suspend fun nftsForOwner(
        owner: EvmAddress,
        pageKey: String? = null,
        pageSize: Int = 50,
        excludeSpam: Boolean = true,
    ): NftPage
}
