package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvmJsonRpcClientTest {

    private val account = EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
    private val token = EvmAddress.parse("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359")
    private val primary = RpcEndpoint("primary", "https://primary.example")
    private val fallback = RpcEndpoint("fallback", "https://fallback.example")

    /** Builds a client whose mock answers each method from [results] (raw JSON value text). */
    private fun client(
        endpoints: List<RpcEndpoint> = listOf(primary),
        results: Map<String, String>,
    ): EvmRpcClient {
        val engine = MockEngine { request ->
            val body = (request.body as TextContent).text
            val obj = rpcJson.parseToJsonElement(body).jsonObject
            val method = obj["method"]!!.jsonPrimitive.content
            val id = obj["id"]!!.jsonPrimitive.content
            val result = results[method] ?: error("unexpected method $method")
            respond(
                """{"jsonrpc":"2.0","id":$id,"result":$result}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return EvmJsonRpcClient(endpoints, HttpClient(engine) {
            install(ContentNegotiation) { json(rpcJson) }
        })
    }

    @Test
    fun readsNativeBalanceAndNonce() = runTest {
        val c = client(results = mapOf(
            "eth_getBalance" to "\"0x1bc16d674ec80000\"", // 2 ETH
            "eth_getTransactionCount" to "\"0x5\"",
        ))
        assertEquals(Quantity.ofHex("0x1bc16d674ec80000"), c.getBalance(account))
        assertEquals(Quantity.of(5), c.getTransactionCount(account))
        assertFalse(c.degraded)
    }

    @Test
    fun readsErc20BalanceViaCall() = runTest {
        val hundred = "0x" + "0".repeat(62) + "64" // uint256 = 100
        val c = client(results = mapOf("eth_call" to "\"$hundred\""))
        assertEquals(Quantity.of(100), c.getErc20Balance(token, account))
    }

    @Test
    fun broadcastsRawTransaction() = runTest {
        val hash = "0xabc123"
        val c = client(results = mapOf("eth_sendRawTransaction" to "\"$hash\""))
        assertEquals(hash, c.sendRawTransaction("0x02f8..."))
    }

    @Test
    fun derivesFeeData() = runTest {
        val feeHistory = """{"baseFeePerGas":["0x7","0x8"],"reward":[["0x3b9aca00"]]}""" // base 8, prio 1 gwei
        val c = client(results = mapOf("eth_feeHistory" to feeHistory))
        val fee = c.getFeeData()
        assertEquals(Quantity.of(8), fee.baseFeePerGas)
        assertEquals(Quantity.of(1_000_000_000L), fee.maxPriorityFeePerGas)
        assertEquals(Quantity.of(8L * 2 + 1_000_000_000L), fee.maxFeePerGas)
    }

    @Test
    fun pollsReceiptUntilMined() = runTest {
        var calls = 0
        val receipt = """{"transactionHash":"0xdead","status":"0x1","blockNumber":"0x10","gasUsed":"0x5208"}"""
        val engine = MockEngine { request ->
            val id = rpcJson.parseToJsonElement((request.body as TextContent).text).jsonObject["id"]!!.jsonPrimitive.content
            calls++
            val result = if (calls < 3) "null" else receipt
            respond(
                """{"jsonrpc":"2.0","id":$id,"result":$result}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val c = EvmJsonRpcClient(listOf(primary), HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } })
        val r = c.awaitReceipt("0xdead", pollIntervalMillis = 1_000, timeoutMillis = 60_000)
        assertEquals(ReceiptStatus.SUCCESS, r.status)
        assertEquals(Quantity.ofHex("0x10"), r.blockNumber)
        assertTrue(calls >= 3)
    }

    @Test
    fun failsOverToFallbackAndReportsDegraded() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host.startsWith("primary")) throw RuntimeException("primary down")
            val id = rpcJson.parseToJsonElement((request.body as TextContent).text).jsonObject["id"]!!.jsonPrimitive.content
            respond(
                """{"jsonrpc":"2.0","id":$id,"result":"0x0"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val c = EvmJsonRpcClient(listOf(primary, fallback), HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } })
        assertEquals(Quantity.ZERO, c.getTransactionCount(account))
        assertTrue(c.degraded, "should report degraded after falling back")
    }

    @Test
    fun throwsWhenAllProvidersFail() = runTest {
        val engine = MockEngine { throw RuntimeException("down") }
        val c = EvmJsonRpcClient(listOf(primary, fallback), HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } })
        assertFailsWith<RpcException.AllProvidersFailed> { c.getBalance(account) }
    }

    @Test
    fun surfacesNodeError() = runTest {
        val engine = MockEngine { request ->
            val id = rpcJson.parseToJsonElement((request.body as TextContent).text).jsonObject["id"]!!.jsonPrimitive.content
            respond(
                """{"jsonrpc":"2.0","id":$id,"error":{"code":-32000,"message":"execution reverted"}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val c = EvmJsonRpcClient(listOf(primary, fallback), HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } })
        assertFailsWith<RpcException.Node> { c.getBalance(account) }
    }
}
