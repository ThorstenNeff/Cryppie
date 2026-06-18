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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlchemyTransfersClientTest {

    private val account = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(engine: MockEngine) = HttpClient(engine) { install(ContentNegotiation) { json(rpcJson) } }

    @Test
    fun parsesExternalEthAndErc20TransfersWithPageKey() = runTest {
        val engine = MockEngine {
            ok(
                """{"jsonrpc":"2.0","id":1,"result":{"transfers":[
                  {"hash":"0xaa","from":"0x0000000000000000000000000000000000000000","to":"0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266","category":"external","asset":"ETH","rawContract":{"value":"0x0de0b6b3a7640000","address":null,"decimal":"0x12"},"metadata":{"blockTimestamp":"2026-01-01T00:00:00.000Z"}},
                  {"hash":"0xbb","from":"0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266","to":"0x70997970C51812dc3A010C7d01b50e0d17dc79C8","category":"erc20","asset":"USDC","rawContract":{"value":"0x1e8480","address":"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48","decimal":"0x6"},"metadata":{"blockTimestamp":"2026-02-01T00:00:00.000Z"}}
                ],"pageKey":"NEXT"}}""",
            )
        }
        val page = AlchemyTransfersClient("https://rpc", 1L, client(engine)).assetTransfers(account, TransferDirection.RECEIVED)

        assertEquals(2, page.transfers.size)
        assertEquals("NEXT", page.nextPageKey)

        val eth = page.transfers[0]
        assertEquals(TransferCategory.EXTERNAL, eth.category)
        assertEquals(Quantity.ofHex("0x0de0b6b3a7640000"), eth.rawValue) // 1 ETH
        assertEquals(18, eth.decimals)
        assertNull(eth.contract)
        assertEquals("2026-01-01T00:00:00.000Z", eth.blockTimestampIso)

        val erc20 = page.transfers[1]
        assertEquals(TransferCategory.ERC20, erc20.category)
        assertEquals(6, erc20.decimals)
        assertEquals("USDC", erc20.asset)
        assertNotNull(erc20.contract)
    }

    @Test
    fun nodeErrorThrows() = runTest {
        val engine = MockEngine { ok("""{"jsonrpc":"2.0","id":1,"error":{"code":-32600,"message":"bad request"}}""") }
        assertFailsWith<RpcException.Node> {
            AlchemyTransfersClient("https://rpc", 1L, client(engine)).assetTransfers(account, TransferDirection.SENT)
        }
    }
}
