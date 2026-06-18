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

/** The transfer-event category from `alchemy_getAssetTransfers`. */
enum class TransferCategory { EXTERNAL, INTERNAL, ERC20, ERC721, ERC1155, UNKNOWN }

/** Which side of the address the query filters on (the API does not allow both at once). */
enum class TransferDirection { SENT, RECEIVED }

/**
 * One normalized asset transfer (KAN-102). Native (external/internal) → [contract] null, [asset] "ETH";
 * ERC-20 → [contract] + [rawValue] + [decimals]. [blockTimestampIso] feeds the time-ordered holdings
 * reconstruction (FIFO).
 */
data class AssetTransfer(
    val chainId: Long,
    val txHash: String?,
    val blockTimestampIso: String?,
    val from: EvmAddress?,
    val to: EvmAddress?,
    val category: TransferCategory,
    val asset: String?,
    val contract: EvmAddress?,
    val rawValue: Quantity?,
    val decimals: Int?,
)

/** A page of transfers + the key for the next page (null when exhausted). */
data class TransferPage(val transfers: List<AssetTransfer>, val nextPageKey: String?)

/**
 * Alchemy **`alchemy_getAssetTransfers`** JSON-RPC client (KAN-102) — the transfer history feeding the
 * FIFO cost-basis / value-series reconstruction. [rpcUrl] is the key-bearing Alchemy RPC endpoint
 * (build-config, never committed). One direction per call (API constraint); the caller pages + merges
 * SENT/RECEIVED. Errors → [RpcException].
 */
class AlchemyTransfersClient(
    private val rpcUrl: String,
    private val chainId: Long,
    private val httpClient: HttpClient = defaultRpcHttpClient(),
) {
    suspend fun assetTransfers(
        address: EvmAddress,
        direction: TransferDirection,
        fromBlock: String = "0x0",
        pageKey: String? = null,
        maxCount: Int = 100,
    ): TransferPage {
        val params = TransfersParams(
            fromBlock = fromBlock,
            fromAddress = if (direction == TransferDirection.SENT) address.value else null,
            toAddress = if (direction == TransferDirection.RECEIVED) address.value else null,
            category = listOf("external", "internal", "erc20", "erc721", "erc1155"),
            maxCount = "0x" + maxCount.toString(16),
            pageKey = pageKey,
        )
        val response = try {
            httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(TransfersRpcRequest(params = listOf(params)))
            }
        } catch (e: RpcException) {
            throw e
        } catch (e: Throwable) {
            throw RpcException.AllProvidersFailed("getAssetTransfers request failed", e)
        }
        if (!response.status.isSuccess()) {
            throw RpcException.AllProvidersFailed("getAssetTransfers HTTP ${response.status.value}")
        }
        val dto = try {
            response.body<TransfersRpcResponse>()
        } catch (e: Throwable) {
            throw RpcException.Decoding("getAssetTransfers response could not be parsed")
        }
        dto.error?.let { throw RpcException.Node(it.code, it.message) }
        val result = dto.result ?: throw RpcException.Decoding("getAssetTransfers missing result")
        return TransferPage(result.transfers.map { it.toTransfer() }, result.pageKey)
    }

    private fun RawTransfer.toTransfer(): AssetTransfer = AssetTransfer(
        chainId = chainId,
        txHash = hash,
        blockTimestampIso = metadata?.blockTimestamp,
        from = from?.let(::address20),
        to = to?.let(::address20),
        category = when (category?.lowercase()) {
            "external" -> TransferCategory.EXTERNAL
            "internal" -> TransferCategory.INTERNAL
            "erc20" -> TransferCategory.ERC20
            "erc721" -> TransferCategory.ERC721
            "erc1155" -> TransferCategory.ERC1155
            else -> TransferCategory.UNKNOWN
        },
        asset = asset,
        contract = rawContract?.address?.let(::address20),
        rawValue = rawContract?.value?.let { runCatching { Quantity.ofHex(it) }.getOrNull() },
        decimals = rawContract?.decimal?.let { runCatching { Quantity.ofHex(it).toLong().toInt() }.getOrNull() },
    )

    private fun address20(hex: String): EvmAddress? =
        Hex.decodeOrNull(hex)?.takeIf { it.size == 20 }?.let { EvmAddress.fromBytes(it) }
}

@Serializable
private data class TransfersRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int = 1,
    val method: String = "alchemy_getAssetTransfers",
    val params: List<TransfersParams>,
)

@Serializable
private data class TransfersParams(
    val fromBlock: String = "0x0",
    val toBlock: String = "latest",
    val fromAddress: String? = null,
    val toAddress: String? = null,
    val category: List<String>,
    val withMetadata: Boolean = true,
    val excludeZeroValue: Boolean = true,
    val maxCount: String,
    val order: String = "asc",
    val pageKey: String? = null,
)

@Serializable
private data class TransfersRpcResponse(val result: TransfersResult? = null, val error: TransfersRpcError? = null)

@Serializable
private data class TransfersRpcError(val code: Int = 0, val message: String = "")

@Serializable
private data class TransfersResult(val transfers: List<RawTransfer> = emptyList(), val pageKey: String? = null)

@Serializable
private data class RawTransfer(
    val hash: String? = null,
    val from: String? = null,
    val to: String? = null,
    val category: String? = null,
    val asset: String? = null,
    val rawContract: RawContract? = null,
    val metadata: TransferMetadata? = null,
)

@Serializable
private data class RawContract(val value: String? = null, val address: String? = null, val decimal: String? = null)

@Serializable
private data class TransferMetadata(val blockTimestamp: String? = null)
