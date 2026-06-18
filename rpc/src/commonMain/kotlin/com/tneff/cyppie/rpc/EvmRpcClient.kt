package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity

/** A configured JSON-RPC endpoint. [url] embeds the API key, injected from build-config — never committed (PRD-02 §9). */
data class RpcEndpoint(val name: String, val url: String)

/** EIP-1559 fee suggestion derived from `eth_feeHistory` + base fee. */
data class FeeData(
    val baseFeePerGas: Quantity,
    val maxPriorityFeePerGas: Quantity,
    val maxFeePerGas: Quantity,
)

enum class ReceiptStatus { SUCCESS, FAILED }

/** A mined transaction receipt (the fields PRD-02 needs for send-status). */
data class TransactionReceipt(
    val transactionHash: String,
    val status: ReceiptStatus,
    val blockNumber: Quantity?,
    val gasUsed: Quantity?,
)

/**
 * High-level read/write EVM RPC over one chain, with transparent provider failover (FR-4).
 * Suspending; the caller controls dispatchers. Web-capable (read path) — no secp256k1 (ADR-0016).
 */
interface EvmRpcClient {

    /** True while a non-primary provider is in use (degraded state, FR-4). */
    val degraded: Boolean

    /** Native balance (wei) — `eth_getBalance`. */
    suspend fun getBalance(address: EvmAddress): Quantity

    /** ERC-20 balance — `eth_call` of `balanceOf(owner)` on [token]. */
    suspend fun getErc20Balance(token: EvmAddress, owner: EvmAddress): Quantity

    /** Account nonce — `eth_getTransactionCount` (pending tag by default). */
    suspend fun getTransactionCount(address: EvmAddress, pending: Boolean = true): Quantity

    /** EIP-1559 fee suggestion — `eth_feeHistory`. */
    suspend fun getFeeData(): FeeData

    /** Raw `eth_call`; returns the ABI-encoded return bytes. */
    suspend fun call(to: EvmAddress, data: ByteArray): ByteArray

    /** Broadcast a signed raw tx (L2's `rawTransactionHex`) — `eth_sendRawTransaction`; returns tx hash. */
    suspend fun sendRawTransaction(rawTransactionHex: String): String

    /** Single receipt poll — `eth_getTransactionReceipt`; null while still pending. */
    suspend fun getTransactionReceipt(txHash: String): TransactionReceipt?

    /** Polls until the tx is mined or [timeoutMillis] elapses; throws [RpcException.ReceiptTimeout]. */
    suspend fun awaitReceipt(
        txHash: String,
        pollIntervalMillis: Long = 4_000,
        timeoutMillis: Long = 120_000,
    ): TransactionReceipt

    companion object {
        /** Production client over [endpoints] (primary first), using the platform Ktor engine. */
        fun create(endpoints: List<RpcEndpoint>): EvmRpcClient =
            EvmJsonRpcClient(endpoints, defaultRpcHttpClient())
    }
}
