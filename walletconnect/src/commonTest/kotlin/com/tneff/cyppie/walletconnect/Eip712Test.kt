package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.RecoverableSignature
import com.tneff.cyppie.wallet.SeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        // Missing "primaryType" → fail-closed with a wrapped exception (L1), not a raw NoSuchElementException.
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            Eip712.encode("""{"types":{"EIP712Domain":[]},"domain":{},"message":{}}""")
        }
    }

    @Test
    fun signTypedDataV4RecoversToSigner() {
        val keyManager = EvmKeyManager(
            SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed()),
        )
        val signer = WalletConnectSigner(keyManager)
        val account0 = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")

        val sig = signer.signTypedDataV4(WcSigningRequest.SignTypedDataV4(account0, mailTypedData), accountIndex = 0)
        assertEquals(65, sig.size)
        val v = sig[64].toInt() and 0xFF
        val recovered = RecoverableSignature(
            r = sig.copyOfRange(0, 32),
            s = sig.copyOfRange(32, 64),
            recId = v - 27,
        ).recoverAddress(Eip712.encode(mailTypedData))
        assertEquals(account0, recovered)
    }
}
