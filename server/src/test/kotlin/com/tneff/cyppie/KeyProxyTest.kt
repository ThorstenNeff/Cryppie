package com.tneff.cyppie

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KAN-112 key-proxy (ADR-0021). Verifies the security contract: the API key is injected **server-side**
 * into the upstream URL (never seen by the client), requests pass through faithfully (method/query/body),
 * unsupported networks and a missing key are rejected, and the per-client rate limit fires.
 */
class KeyProxyTest {

    private class Captured {
        var url: String = ""
        var method: HttpMethod = HttpMethod.Get
        var body: String = ""
    }

    /** A forwarding client whose MockEngine records the upstream request and returns [upstreamBody]. */
    private fun mockClient(captured: Captured, upstreamBody: String = """{"ok":true}"""): HttpClient {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method
            captured.body = when (val b = request.body) {
                is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                else -> ""
            }
            respond(upstreamBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HttpClient(engine) { expectSuccess = false }
    }

    @Test
    fun dataRequestInjectsKeyServerSideAndPassesBodyThrough() = testApplication {
        val captured = Captured()
        application { module(ProxyConfig(alchemyApiKey = "SECRET_KEY"), mockClient(captured)) }

        val reqBody = """{"addresses":[{"address":"0xabc","networks":["eth-mainnet"]}]}"""
        val response = client.post("/alchemy/data/v1/assets/tokens/by-address") {
            contentType(ContentType.Application.Json)
            setBody(reqBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"ok":true}""", response.bodyAsText()) // upstream body relayed verbatim
        // The key is injected into the upstream URL — and only there.
        assertEquals("https://api.g.alchemy.com/data/v1/SECRET_KEY/assets/tokens/by-address", captured.url)
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals(reqBody, captured.body)
    }

    @Test
    fun nftRequestIsPathScopedAndForwardsQuery() = testApplication {
        val captured = Captured()
        application { module(ProxyConfig(alchemyApiKey = "K"), mockClient(captured)) }

        val response = client.get("/alchemy/nft/v3/eth-mainnet/getNFTsForOwner?owner=0xf39&pageSize=50")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(captured.url.startsWith("https://eth-mainnet.g.alchemy.com/nft/v3/K/getNFTsForOwner"))
        assertTrue("owner=0xf39" in captured.url, "query not forwarded: ${captured.url}")
        assertTrue("pageSize=50" in captured.url)
    }

    @Test
    fun rpcRequestMapsToNetworkRpcEndpoint() = testApplication {
        val captured = Captured()
        application { module(ProxyConfig(alchemyApiKey = "K"), mockClient(captured)) }

        val response = client.post("/alchemy/rpc/v2/base-mainnet") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("https://base-mainnet.g.alchemy.com/v2/K", captured.url)
    }

    @Test
    fun unsupportedNetworkIsRejected() = testApplication {
        application { module(ProxyConfig(alchemyApiKey = "K"), mockClient(Captured())) }
        val response = client.post("/alchemy/rpc/v2/polygon-mainnet") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun missingKeyDegradesWith503() = testApplication {
        val captured = Captured()
        application { module(ProxyConfig(alchemyApiKey = null), mockClient(captured)) }
        val response = client.post("/alchemy/data/v1/assets/tokens/by-address") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("", captured.url) // never reached upstream → no key, no leak
    }

    @Test
    fun rateLimitFiresPerClient() = testApplication {
        application {
            module(ProxyConfig(alchemyApiKey = "K", rateLimitPerMinute = 1), mockClient(Captured()))
        }
        val first = client.get("/alchemy/nft/v3/eth-mainnet/getNFTsForOwner?owner=0x1")
        val second = client.get("/alchemy/nft/v3/eth-mainnet/getNFTsForOwner?owner=0x1")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
    }

    @Test
    fun healthReportsKeyStatusWithoutLeakingIt() = testApplication {
        application { module(ProxyConfig(alchemyApiKey = "SECRET"), mockClient(Captured())) }
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue("\"keyConfigured\":true" in body)
        assertTrue("SECRET" !in body) // never echo the key
    }
}
