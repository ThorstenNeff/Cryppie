package com.tneff.cyppie.evm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-143 — EIP-712 digest vectors for the lifted `:evm` [Eip712]. The known-answer is the canonical
 * EIP-712 "Mail" digest from the standard; the authoritative recover-to-signer vector (full 65-byte sig
 * vs eth-account) lives in `:walletconnect` (`WcSignerExternalVectorsTest`), which exercises this builder
 * through the signer and must stay green = byte-identity proof of the lift.
 */
class Eip712Test {

    // The canonical EIP-712 "Mail" example from the standard.
    private val mailTypedData = """
        {
          "types": {
            "EIP712Domain": [
              {"name":"name","type":"string"},
              {"name":"version","type":"string"},
              {"name":"chainId","type":"uint256"},
              {"name":"verifyingContract","type":"address"}
            ],
            "Person": [
              {"name":"name","type":"string"},
              {"name":"wallet","type":"address"}
            ],
            "Mail": [
              {"name":"from","type":"Person"},
              {"name":"to","type":"Person"},
              {"name":"contents","type":"string"}
            ]
          },
          "primaryType": "Mail",
          "domain": {
            "name": "Ether Mail",
            "version": "1",
            "chainId": 1,
            "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
          },
          "message": {
            "from": {"name":"Cow","wallet":"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"},
            "to": {"name":"Bob","wallet":"0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"},
            "contents": "Hello, Bob!"
          }
        }
    """.trimIndent()

    @Test
    fun digestMatchesCanonicalEip712MailVector() {
        // Authoritative known-answer from the EIP-712 specification.
        assertEquals(
            "be609aee343fb3c4b28e1df9e632fca64fcfaede20f02e86244efddf30957bd2",
            Hex.encode(Eip712.encode(mailTypedData)),
        )
    }

    @Test
    fun malformedTypedDataFailsClosed() {
        // Missing "primaryType" → fail-closed with a wrapped Eip712Exception, not a raw NoSuchElementException.
        assertFailsWith<Eip712Exception> {
            Eip712.encode("""{"types":{"EIP712Domain":[]},"domain":{},"message":{}}""")
        }
    }

    private fun singleField(type: String, value: String) = """
        {
          "types": { "EIP712Domain": [{"name":"name","type":"string"}], "T": [{"name":"x","type":"$type"}] },
          "primaryType": "T",
          "domain": { "name": "d" },
          "message": { "x": $value }
        }
    """.trimIndent()

    @Test
    fun rejectsUintExceedingDeclaredWidth() {
        assertFailsWith<Eip712Exception> { Eip712.encode(singleField("uint8", "300")) }
        Eip712.encode(singleField("uint8", "200")) // valid uint8 encodes fine
    }

    @Test
    fun rejectsHexNegativeAndOutOfRangeInt() {
        assertFailsWith<Eip712Exception> { Eip712.encode(singleField("int8", "\"-0x5\"")) }
        assertFailsWith<Eip712Exception> { Eip712.encode(singleField("int8", "200")) } // > 127
        Eip712.encode(singleField("int8", "\"-128\"")) // legitimate most-negative int8
    }
}
