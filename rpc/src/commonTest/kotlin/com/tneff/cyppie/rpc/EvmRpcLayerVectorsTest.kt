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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KAN-61 — RPC-layer reliability KATs with Ktor [MockEngine] (no live provider, ADR-0010/0011).
 * Complements the Dev self-tests with the contract-specific cases: node-error mapping
 * (nonce-too-low / insufficient-funds → [RpcException.Node]), HTTP-status failover + degraded
 * recovery, receipt FAILED / pending-null / await-timeout, malformed-result decoding, and
 * all-providers-down. No secrets/keys in any fixture.
 */
class EvmRpcLayerVectorsTest {

    private val account = EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
    private val primary = RpcEndpoint("primary", "https://primary.example")
    private val fallback = RpcEndpoint("fallback", "https://fallback.example")

    private fun http(engine: MockEngine) =
        HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } }

    private fun reqId(request: io.ktor.client.request.HttpRequestData): String =
        rpcJson.parseToJsonElement((request.body as TextContent).text).jsonObject["id"]!!.jsonPrimitive.content

    private fun method(request: io.ktor.client.request.HttpRequestData): String =
        rpcJson.parseToJsonElement((request.body as TextContent).text).jsonObject["method"]!!.jsonPrimitive.content

    private fun client(endpoints: List<RpcEndpoint> = listOf(primary), results: Map<String, String>): EvmRpcClient {
        val engine = MockEngine { request ->
            val m = method(request)
            val result = results[m] ?: error("unexpected method $m")
            respond(
                """{"jsonrpc":"2.0","id":${reqId(request)},"result":$result}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return EvmJsonRpcClient(endpoints, http(engine))
    }

    @Test
    fun mapsNonceTooLowAndInsufficientFundsToNodeError() = runTest {
        for (msg in listOf("nonce too low", "insufficient funds for gas * price + value")) {
            val engine = MockEngine { request ->
                respond(
                    """{"jsonrpc":"2.0","id":${reqId(request)},"error":{"code":-32000,"message":"$msg"}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val c = EvmJsonRpcClient(listOf(primary, fallback), http(engine))
            // -32000 is authoritative (not retryable) → surfaced immediately, no failover.
            val ex = assertFailsWith<RpcException.Node> { c.sendRawTransaction("0x02f8aa") }
            assertEquals(-32000, ex.code)
            assertTrue(ex.message!!.contains(msg), "message should carry the node reason")
            assertFalse(c.degraded, "authoritative node error must not mark degraded")
        }
    }

    @Test
    fun httpErrorStatusFailsOverThenRecovers() = runTest {
        var primaryHealthy = false
        val engine = MockEngine { request ->
            if (request.url.host.startsWith("primary") && !primaryHealthy) {
                respond("rate limited", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "text/plain"))
            } else {
                respond(
                    """{"jsonrpc":"2.0","id":${reqId(request)},"result":"0x5"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val c = EvmJsonRpcClient(listOf(primary, fallback), http(engine))
        // Primary 429 → fallback serves → degraded.
        assertEquals(Quantity.of(5), c.getTransactionCount(account))
        assertTrue(c.degraded)
        // Primary recovers → next call uses it → degraded clears.
        primaryHealthy = true
        assertEquals(Quantity.of(5), c.getTransactionCount(account))
        assertFalse(c.degraded, "degraded must clear once the primary serves again")
    }

    @Test
    fun allProvidersHttpErrorThrowsAllProvidersFailed() = runTest {
        val engine = MockEngine {
            respond("oops", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "text/plain"))
        }
        val c = EvmJsonRpcClient(listOf(primary, fallback), http(engine))
        assertFailsWith<RpcException.AllProvidersFailed> { c.getBalance(account) }
    }

    @Test
    fun receiptFailedStatusAndPendingNull() = runTest {
        val failed = """{"transactionHash":"0xdead","status":"0x0","blockNumber":"0x10","gasUsed":"0x5208"}"""
        val cFailed = client(results = mapOf("eth_getTransactionReceipt" to failed))
        val r = cFailed.getTransactionReceipt("0xdead")
        assertEquals(ReceiptStatus.FAILED, r!!.status)
        assertEquals(Quantity.ofHex("0x5208"), r.gasUsed)

        val cPending = client(results = mapOf("eth_getTransactionReceipt" to "null"))
        assertNull(cPending.getTransactionReceipt("0xdead"))
    }

    @Test
    fun awaitReceiptTimesOutWhenNeverMined() = runTest {
        val c = client(results = mapOf("eth_getTransactionReceipt" to "null"))
        assertFailsWith<RpcException.ReceiptTimeout> {
            c.awaitReceipt("0xdead", pollIntervalMillis = 1_000, timeoutMillis = 5_000)
        }
    }

    @Test
    fun malformedResultThrowsDecoding() = runTest {
        // eth_getBalance returns an object instead of a hex string → not decodable to a Quantity.
        val c = client(results = mapOf("eth_getBalance" to """{"unexpected":true}"""))
        assertFailsWith<RpcException.Decoding> { c.getBalance(account) }
    }
}
