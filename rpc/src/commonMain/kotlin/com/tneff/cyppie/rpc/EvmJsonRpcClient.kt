package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.evm.abi.Erc20Abi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * [EvmRpcClient] over a list of JSON-RPC [endpoints] (primary first) with transparent failover:
 * a transport failure rolls over to the next provider; a JSON-RPC `error` is authoritative and is
 * surfaced (failover wouldn't help). [degraded] reflects use of a non-primary provider (FR-4).
 */
internal class EvmJsonRpcClient(
    private val endpoints: List<RpcEndpoint>,
    private val http: HttpClient,
) : EvmRpcClient {

    init { require(endpoints.isNotEmpty()) { "At least one RPC endpoint is required" } }

    private var requestId = 0
    private var _degraded = false
    override val degraded: Boolean get() = _degraded

    override suspend fun getBalance(address: EvmAddress): Quantity =
        rpc("eth_getBalance", buildJsonArray { add(address.value); add("latest") }).asQuantity()

    override suspend fun getErc20Balance(token: EvmAddress, owner: EvmAddress): Quantity =
        Erc20Abi.decodeUint(call(token, Erc20Abi.balanceOf(owner)))

    override suspend fun getTransactionCount(address: EvmAddress, pending: Boolean): Quantity =
        rpc(
            "eth_getTransactionCount",
            buildJsonArray { add(address.value); add(if (pending) "pending" else "latest") },
        ).asQuantity()

    override suspend fun call(to: EvmAddress, data: ByteArray): ByteArray {
        val result = rpc("eth_call", buildJsonArray {
            addJsonObject {
                put("to", to.value)
                put("data", "0x" + Hex.encode(data))
            }
            add("latest")
        })
        return Hex.decodeOrNull(result.asHexString()) ?: throw RpcException.Decoding("eth_call result is not hex")
    }

    override suspend fun getFeeData(): FeeData =
        runCatching { feeDataFromHistory() }.getOrNull() ?: feeDataFromGasPrice()

    /** EIP-1559 fee from `eth_feeHistory`; tolerates an empty/absent `reward` array. */
    private suspend fun feeDataFromHistory(): FeeData {
        val o = rpc("eth_feeHistory", buildJsonArray {
            add("0x1"); add("latest"); addJsonArray { add(50) }
        }).jsonObject
        val baseFee = Quantity.ofHex(
            o["baseFeePerGas"]?.jsonArray?.lastOrNull()?.jsonPrimitive?.content
                ?: throw RpcException.Decoding("feeHistory missing baseFeePerGas"),
        )
        val priority = o["reward"]?.jsonArray?.firstOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?.let { Quantity.ofHex(it) } ?: DEFAULT_PRIORITY_FEE
        // Standard suggestion: maxFee = 2 * baseFee + priority (fits Long for realistic gas prices).
        val maxFee = Quantity.of(baseFee.toLong() * 2 + priority.toLong())
        return FeeData(baseFeePerGas = baseFee, maxPriorityFeePerGas = priority, maxFeePerGas = maxFee)
    }

    /** Fallback when a provider lacks `eth_feeHistory`: `eth_gasPrice` (+ `eth_maxPriorityFeePerGas`). */
    private suspend fun feeDataFromGasPrice(): FeeData {
        val gasPrice = rpc("eth_gasPrice", buildJsonArray {}).asQuantity()
        val priority = runCatching { rpc("eth_maxPriorityFeePerGas", buildJsonArray {}).asQuantity() }
            .getOrNull() ?: DEFAULT_PRIORITY_FEE
        // Treat gasPrice as the effective base; pad maxFee by the priority tip.
        val maxFee = Quantity.of(gasPrice.toLong() + priority.toLong())
        return FeeData(baseFeePerGas = gasPrice, maxPriorityFeePerGas = priority, maxFeePerGas = maxFee)
    }

    override suspend fun sendRawTransaction(rawTransactionHex: String): String =
        rpc("eth_sendRawTransaction", buildJsonArray { add(rawTransactionHex) }).asHexString()

    override suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? {
        val result = rpc("eth_getTransactionReceipt", buildJsonArray { add(txHash) })
        if (result is JsonNull) return null
        val o = result as? JsonObject ?: throw RpcException.Decoding("receipt is not an object")
        val status = when (o["status"]?.jsonPrimitive?.content) {
            "0x1" -> ReceiptStatus.SUCCESS
            "0x0" -> ReceiptStatus.FAILED
            else -> ReceiptStatus.SUCCESS // pre-Byzantium receipts have no status; treat mined as success
        }
        return TransactionReceipt(
            transactionHash = o["transactionHash"]?.jsonPrimitive?.content ?: txHash,
            status = status,
            blockNumber = o["blockNumber"]?.jsonPrimitive?.content?.let { Quantity.ofHex(it) },
            gasUsed = o["gasUsed"]?.jsonPrimitive?.content?.let { Quantity.ofHex(it) },
        )
    }

    override suspend fun awaitReceipt(
        txHash: String,
        pollIntervalMillis: Long,
        timeoutMillis: Long,
    ): TransactionReceipt {
        var elapsed = 0L
        while (true) {
            getTransactionReceipt(txHash)?.let { return it }
            if (elapsed >= timeoutMillis) {
                throw RpcException.ReceiptTimeout("Receipt for $txHash not found within ${timeoutMillis}ms")
            }
            delay(pollIntervalMillis)
            elapsed += pollIntervalMillis
        }
    }

    /**
     * Performs one JSON-RPC call with provider failover; returns the `result` element (or [JsonNull]).
     *
     * Rolls over to the next provider on a transport failure, a non-success HTTP status (e.g. 429),
     * or a *retryable* JSON-RPC error (rate-limit / temporarily-unavailable, [RETRYABLE_RPC_CODES]).
     * An authoritative node error (execution revert, invalid params) is surfaced immediately —
     * another provider would only return the same.
     */
    private suspend fun rpc(method: String, params: JsonArray): JsonElement {
        val id = ++requestId
        var lastError: Throwable? = null
        for ((index, endpoint) in endpoints.withIndex()) {
            try {
                val httpResponse = http.post(endpoint.url) {
                    contentType(ContentType.Application.Json)
                    setBody(JsonRpcRequest(id = id, method = method, params = params))
                }
                if (!httpResponse.status.isSuccess()) {
                    lastError = RpcException.Transport("HTTP ${httpResponse.status.value} from ${endpoint.name}")
                    continue // provider-side issue (429/5xx) → next provider
                }
                val response: JsonRpcResponse = httpResponse.body()
                val error = response.error
                if (error != null) {
                    if (error.code in RETRYABLE_RPC_CODES) {
                        lastError = RpcException.Node(error.code, error.message)
                        continue // rate-limited / transient → next provider
                    }
                    throw RpcException.Node(error.code, error.message) // authoritative
                }
                _degraded = index > 0
                return response.result ?: JsonNull
            } catch (e: RpcException.Node) {
                throw e // authoritative node error
            } catch (e: Throwable) {
                lastError = e // transport failure → roll over to the next provider
            }
        }
        throw RpcException.AllProvidersFailed("All ${endpoints.size} provider(s) failed", lastError)
    }

    private companion object {
        /** JSON-RPC error codes worth retrying on another provider: rate-limit / transient. */
        val RETRYABLE_RPC_CODES = setOf(-32005, -32016, -32029, -32603, 429)

        /** 1.5 gwei priority tip when a provider exposes no reward/priority data. */
        val DEFAULT_PRIORITY_FEE = Quantity.of(1_500_000_000L)
    }

    private fun JsonElement.asHexString(): String =
        (this as? JsonPrimitive)?.content ?: throw RpcException.Decoding("expected hex string, got $this")

    private fun JsonElement.asQuantity(): Quantity = Quantity.ofHex(asHexString())
}
