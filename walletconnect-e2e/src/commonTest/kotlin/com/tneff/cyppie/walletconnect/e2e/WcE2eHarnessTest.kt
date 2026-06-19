package com.tneff.cyppie.walletconnect.e2e

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.walletconnect.WcEvent
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** KAN-127 — the deterministic WC E2E harness ([FakeWalletConnectController] + [WcE2eScript] + [WcE2e]). */
class WcE2eHarnessTest {

    private val addr = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val sepolia = "eip155:11155111"

    private fun scriptJson(chain: String = sepolia) = """
        {"topic":"t-e2e","chain":"$chain","accounts":["$chain:$addr"],
         "dappName":"Acme","dappUrl":"https://acme.test",
         "requests":[{"id":7,"method":"personal_sign","params":"[\"0x48656c6c6f\",\"$addr\"]"}]}
    """.trimIndent()

    @Test
    fun parsesScriptFields() {
        val s = WcE2eScript.parse(scriptJson())
        assertEquals("t-e2e", s.topic)
        assertEquals(sepolia, s.chain)
        assertEquals(listOf("$sepolia:$addr"), s.accounts)
        assertEquals("Acme", s.dappName)
        assertEquals(1, s.requests.size)
        assertEquals(7L, s.requests[0].requestId)
        assertEquals("personal_sign", s.requests[0].method)
    }

    @Test
    fun refusesProductionMainnetChains() {
        // 🔒 never mainnet — the structural guard, both supported mainnets.
        assertFailsWith<IllegalArgumentException> { WcE2eScript.parse(scriptJson(chain = "eip155:1")) }
        assertFailsWith<IllegalArgumentException> { WcE2eScript.parse(scriptJson(chain = "eip155:8453")) }
    }

    @Test
    fun rejectsMalformedOrIncompleteScript() {
        assertFailsWith<IllegalArgumentException> { WcE2eScript.parse("not json") }
        assertFailsWith<IllegalArgumentException> { WcE2eScript.parse("""{"chain":"$sepolia"}""") } // no topic
        assertFailsWith<IllegalArgumentException> { WcE2eScript.parse("""{"topic":"t"}""") } // no chain
    }

    @Test
    fun pairEmitsProposalThenApproveSettlesAndReplaysRequests() = runTest {
        val fake = FakeWalletConnectController(WcE2eScript.parse(scriptJson()))
        fake.pair("wc:deadbeef@2?relay-protocol=irn&symKey=00")
        fake.approveSession("t-e2e", listOf("$sepolia:$addr"))

        // proposal + settled + 1 request, in order (SharedFlow replay delivers them to this collector).
        val events = fake.events.take(3).toList()
        assertTrue(events[0] is WcEvent.OnSessionProposal)
        assertEquals(setOf(sepolia), (events[0] as WcEvent.OnSessionProposal).proposal.chains.toSet())
        assertTrue(events[1] is WcEvent.OnSessionSettled)
        val req = events[2] as WcEvent.OnSessionRequest
        assertEquals(7L, req.request.requestId)
        assertEquals("personal_sign", req.request.method)
        assertEquals(sepolia, req.request.chainId)
    }

    @Test
    fun bindsApprovedChainsAndAccountsAfterApproval() = runTest {
        val fake = FakeWalletConnectController(WcE2eScript.parse(scriptJson()))
        // Empty before approval, populated from the approved accounts afterwards.
        assertTrue(fake.approvedChains("t-e2e").isEmpty())
        fake.approveSession("t-e2e", listOf("$sepolia:$addr"))
        assertEquals(setOf(11155111L), fake.approvedChains("t-e2e"))
        assertEquals(setOf(EvmAddress.parse(addr)), fake.approvedAccounts("t-e2e"))
    }

    @Test
    fun recordsOutboundCallsForAssertions() = runTest {
        val fake = FakeWalletConnectController(WcE2eScript.parse(scriptJson()))
        fake.approveSession("t-e2e", listOf("$sepolia:$addr"))
        fake.respondRequest(7L, "t-e2e", "0xsignature")
        fake.rejectRequest(8L, "t-e2e", "user declined")
        assertEquals(1, fake.approvedSessions.size)
        assertEquals("0xsignature", fake.respondedRequests.single().result)
        assertEquals(1, fake.rejectedRequests.size)
    }

    @Test
    fun factoryIsFailClosed() {
        // L2 belt: a release build (isDebugBuild=false) never yields a fake, even with a valid testnet script.
        assertNull(WcE2e.fakeOrNull(scriptJson(), isDebugBuild = false))
        // Debug build: still rejects blank/garbage/mainnet, accepts a valid testnet script.
        assertNull(WcE2e.fakeOrNull(null, isDebugBuild = true))
        assertNull(WcE2e.fakeOrNull("   ", isDebugBuild = true))
        assertNull(WcE2e.fakeOrNull("garbage{", isDebugBuild = true))
        assertNull(WcE2e.fakeOrNull(scriptJson(chain = "eip155:1"), isDebugBuild = true)) // mainnet → no fake
        assertNotNull(WcE2e.fakeOrNull(scriptJson(), isDebugBuild = true)) // valid testnet script → fake
    }
}
