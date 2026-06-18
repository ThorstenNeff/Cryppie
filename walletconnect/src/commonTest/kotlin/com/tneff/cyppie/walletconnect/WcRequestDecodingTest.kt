package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WcRequestDecodingTest {

    private val dapp = WcDappMetadata(name = "Test Dapp", description = "", url = "https://dapp.example")

    private fun request(method: String, params: String) =
        WcSessionRequest(requestId = 1, topic = "topic", chainId = "eip155:1", method = method, params = params, dapp = dapp)

    @Test
    fun decodesPersonalSign() {
        // params: [message(hex), address]; 0x48656c6c6f = "Hello"
        val decoded = request(
            "personal_sign",
            """["0x48656c6c6f","0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"]""",
        ).decode()
        val signNow = assertIs<WcDecodedRequest.SignNow>(decoded)
        val personal = assertIs<WcSigningRequest.PersonalSign>(signNow.request)
        assertEquals("Hello", personal.message.decodeToString())
        assertEquals("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed", personal.address.value)
    }

    @Test
    fun decodesSignTypedDataV4() {
        val decoded = request(
            "eth_signTypedData_v4",
            """["0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed",{"primaryType":"Mail"}]""",
        ).decode()
        val signNow = assertIs<WcDecodedRequest.SignNow>(decoded)
        val typed = assertIs<WcSigningRequest.SignTypedDataV4>(signNow.request)
        assertTrue(typed.typedDataJson.contains("Mail"))
    }

    @Test
    fun decodesSendTransactionPreservingDappFieldsAndChain() {
        val decoded = request(
            "eth_sendTransaction",
            """[{"from":"0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed","to":"0x70997970c51812dc3a010c7d01b50e0d17dc79c8","value":"0xde0b6b3a7640000","data":"0x"}]""",
        ).decode()
        val send = assertIs<WcDecodedRequest.SendTransaction>(decoded)
        val p = send.params
        assertEquals("eip155:1", p.chainId) // chain-binding (guardrail #4)
        assertEquals("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed", p.from.value)
        assertEquals("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", p.to?.value)
        assertEquals(Quantity.ofHex("0xde0b6b3a7640000"), p.value) // 1 ETH preserved
        assertNull(p.nonce) // L3 fills (guardrail #2 — not defaulted here)
        assertNull(p.maxFeePerGas)
    }

    @Test
    fun unsupportedMethodFailsClosed() {
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            request("eth_signTransaction", "[]").decode()
        }
    }

    @Test
    fun malformedParamsFailClosed() {
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            request("personal_sign", "not-json").decode()
        }
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            request("eth_sendTransaction", "[{}]").decode() // missing "from"
        }
    }
}
