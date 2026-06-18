package com.tneff.cyppie.rpc

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
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

class AlchemyNftClientTest {

    private val owner = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val baseUrl = "https://eth-mainnet.g.alchemy.com/nft/v3/KEY"

    private val erc721 = """
        {"contract":{"address":"0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D","name":"BAYC","tokenType":"ERC721","isSpam":false},
         "tokenId":"42","tokenType":"ERC721","name":"Ape #42",
         "image":{"cachedUrl":"https://cdn.alchemy.com/a.png","thumbnailUrl":"https://cdn.alchemy.com/t.png"},
         "collection":{"name":"Bored Ape Yacht Club"}}
    """.trimIndent()

    private val erc1155 = """
        {"contract":{"address":"0x76BE3b62873462d2142405439777e971754E8E77","tokenType":"ERC1155","isSpam":true},
         "tokenId":"7","tokenType":"ERC1155","balance":"3","image":{"thumbnailUrl":"https://cdn.alchemy.com/t2.png"}}
    """.trimIndent()

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun clientFor(engine: MockEngine) =
        AlchemyNftClient(1L, baseUrl, HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } })

    @Test
    fun parsesErc721AndErc1155() = runTest {
        val engine = MockEngine { ok("""{"ownedNfts":[$erc721,$erc1155],"totalCount":2}""") }
        val page = clientFor(engine).nftsForOwner(owner)

        assertEquals(2, page.items.size)
        val ape = page.items[0]
        assertEquals(NftType.ERC721, ape.type)
        assertEquals("42", ape.tokenId)
        assertEquals("Ape #42", ape.name)
        assertEquals("Bored Ape Yacht Club", ape.collectionName)
        assertEquals("https://cdn.alchemy.com/a.png", ape.imageUrl) // prefers cachedUrl
        assertNull(ape.balance)
        assertFalse(ape.isSpam)

        val sft = page.items[1]
        assertEquals(NftType.ERC1155, sft.type)
        assertEquals(Quantity.of(3), sft.balance)
        assertEquals("https://cdn.alchemy.com/t2.png", sft.imageUrl) // thumbnail fallback
        assertTrue(sft.isSpam)
        assertNull(page.nextPageKey)
        assertEquals(2, page.totalCount)
    }

    @Test
    fun pagingCarriesPageKeyForward() = runTest {
        val engine = MockEngine { request ->
            if (request.url.parameters["pageKey"] == null) {
                ok("""{"ownedNfts":[$erc721],"pageKey":"PAGE2"}""")
            } else {
                ok("""{"ownedNfts":[$erc1155]}""")
            }
        }
        val client = clientFor(engine)
        val first = client.nftsForOwner(owner)
        assertEquals("PAGE2", first.nextPageKey)
        val second = client.nftsForOwner(owner, pageKey = first.nextPageKey)
        assertNull(second.nextPageKey)
        assertEquals("7", second.items.single().tokenId)
    }

    @Test
    fun excludeSpamAddsRequestFilter() = runTest {
        var requestedUrl = ""
        val engine = MockEngine { request -> requestedUrl = request.url.toString(); ok("""{"ownedNfts":[]}""") }
        val page = clientFor(engine).nftsForOwner(owner, excludeSpam = true)
        assertTrue(requestedUrl.contains("SPAM"), "request must carry excludeFilters=SPAM: $requestedUrl")
        assertTrue(page.items.isEmpty())
        assertNull(page.nextPageKey)
    }

    @Test
    fun missingFieldsMapToNullAndUnknownWithoutCrash() = runTest {
        val engine = MockEngine {
            ok("""{"ownedNfts":[{"contract":{"address":"0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D"},"tokenId":"5"}]}""")
        }
        val item = clientFor(engine).nftsForOwner(owner).items.single()
        assertEquals(NftType.UNKNOWN, item.type)
        assertNull(item.name)
        assertNull(item.imageUrl)
        assertNull(item.balance)
        assertFalse(item.isSpam)
    }

    @Test
    fun httpErrorIsAllProvidersFailed() = runTest {
        val engine = MockEngine { respond("upstream error", HttpStatusCode.InternalServerError) }
        assertFailsWith<RpcException.AllProvidersFailed> { clientFor(engine).nftsForOwner(owner) }
    }
}
