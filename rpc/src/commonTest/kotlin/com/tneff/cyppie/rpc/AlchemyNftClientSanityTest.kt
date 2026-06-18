package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KAN-88 — AlchemyNftClient **sanity/edge** KATs (Alchemy v3 `getNFTsForOwner`), complementing the
 * Dev parse tests: missing/optional fields parse gracefully, empty owner, HTTP errors and malformed
 * JSON map to the right [RpcException]. MockEngine, no live provider; no secrets in fixtures.
 */
class AlchemyNftClientSanityTest {

    private val owner = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val baseUrl = "https://eth-mainnet.g.alchemy.com/nft/v3/KEY"

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun clientFor(engine: MockEngine) =
        AlchemyNftClient(1L, baseUrl, HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } })

    @Test
    fun missingOptionalFieldsParseToNullsAndUnknownType() = runTest {
        // Minimal NFT: only contract address + tokenId; no tokenType/name/image/balance/isSpam/collection.
        val minimal = """{"contract":{"address":"0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"},"tokenId":"99"}"""
        val engine = MockEngine { ok("""{"ownedNfts":[$minimal],"totalCount":1}""") }
        val item = clientFor(engine).nftsForOwner(owner).items.single()
        assertEquals(NftType.UNKNOWN, item.type) // unknown/missing tokenType
        assertEquals("99", item.tokenId)
        assertNull(item.name)
        assertNull(item.collectionName)
        assertNull(item.imageUrl)
        assertNull(item.balance)        // balance only for ERC-1155
        assertFalse(item.isSpam)        // missing isSpam → false (not blocked by default)
    }

    @Test
    fun emptyOwnerReturnsEmptyPage() = runTest {
        val engine = MockEngine { ok("""{"ownedNfts":[],"totalCount":0}""") }
        val page = clientFor(engine).nftsForOwner(owner)
        assertTrue(page.items.isEmpty())
        assertNull(page.nextPageKey)
        assertEquals(0, page.totalCount)
        assertFalse(page.degraded)
    }

    @Test
    fun httpErrorMapsToAllProvidersFailed() = runTest {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.TooManyRequests, HttpStatusCode.BadGateway)) {
            val engine = MockEngine { respond("upstream error", status, headersOf(HttpHeaders.ContentType, "text/plain")) }
            assertFailsWith<RpcException.AllProvidersFailed> { clientFor(engine).nftsForOwner(owner) }
        }
    }

    @Test
    fun malformedJsonMapsToDecoding() = runTest {
        val engine = MockEngine { ok("""{"ownedNfts": [ this is not valid json """) }
        assertFailsWith<RpcException.Decoding> { clientFor(engine).nftsForOwner(owner) }
    }
}
