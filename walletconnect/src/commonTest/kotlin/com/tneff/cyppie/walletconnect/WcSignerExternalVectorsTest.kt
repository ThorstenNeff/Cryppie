package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-63 — **externally-referenced** signing KATs for the WalletConnect signer (WC-merge gate).
 *
 * [WalletConnectSignerTest]/[Eip712Test] check `personal_sign`/`eth_signTypedData_v4` by **recomputing
 * the digest test-side and recovering the signer** — which would still pass even if both the signer and
 * the test shared a wrong EIP-191 prefix (the masked-`0x19` trap). These assert the full 65-byte
 * `r‖s‖v` against **eth-account 0.13.7** literals (`Account.sign_message`) under the public Hardhat/Anvil
 * account #0 (`0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266`). A wrong prefix, length encoding, low-S or
 * v offset (27+recId) would diverge from these bytes. Deterministic (RFC-6979) → stable.
 */
class WcSignerExternalVectorsTest {

    private val keyManager = EvmKeyManager(
        SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed()),
    )
    private val signer = WalletConnectSigner(keyManager)
    private val account0 = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")

    @Test
    fun personalSign_helloCyppie_matchesEthAccount() {
        // eth-account: Account.sign_message(encode_defunct(b"Hello Cyppie"), anvil#0).signature
        val sig = signer.personalSign(
            WcSigningRequest.PersonalSign("Hello Cyppie".encodeToByteArray(), account0),
            accountIndex = 0,
        )
        assertEquals(
            "4d3df744461645be1210332f9eec6d729496c38f04365780ef7b2e3618dc5924" +
                "235ee522a19db859b0596f52ad93ad3214d6661b2047e0b3df5e6f2604ea9fc91b",
            Hex.encode(sig),
        )
    }

    @Test
    fun personalSign_nonce42_zeroPaddedR_matchesEthAccount() {
        // r begins with a 0x00 byte (v=28) — guards 32-byte zero-padding of r and the parity offset.
        val sig = signer.personalSign(
            WcSigningRequest.PersonalSign("nonce-42".encodeToByteArray(), account0),
            accountIndex = 0,
        )
        assertEquals(
            "0083735e97244cfeb0c4c0af88472197f555ffd8e33510d271dc665c2222d3a2" +
                "520092920b3bf5dc16d99c68091983e4d8a45b8f8628cd5e1d0b40db955cb3791c",
            Hex.encode(sig),
        )
    }

    @Test
    fun signTypedDataV4_canonicalMail_matchesEthAccount() {
        // eth-account: Account.sign_message(encode_typed_data(full_message=Mail), anvil#0).signature
        // (its message_hash == be609aee…30957bd2, the EIP-712 spec known-answer → oracle validated.)
        val sig = signer.signTypedDataV4(
            WcSigningRequest.SignTypedDataV4(account0, MAIL_TYPED_DATA),
            accountIndex = 0,
        )
        assertEquals(
            "6ea8bb309a3401225701f3565e32519f94a0ea91a5910ce9229fe488e773584c" +
                "0390416a2190d9560219dab757ecca2029e63fa9d1c2aebf676cc25b9f03126a1b",
            Hex.encode(sig),
        )
    }

    private companion object {
        // The canonical EIP-712 "Mail" example from the standard (same input as Eip712Test).
        val MAIL_TYPED_DATA = """
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
    }
}
