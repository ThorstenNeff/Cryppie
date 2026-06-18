package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.wallet.tx.Eip1559Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WcDisclosureTest {

    private val addr = EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")

    @Test
    fun personalSignDisclosesReadableMessage() {
        val d = WcSigningRequest.PersonalSign("Sign in to dApp".encodeToByteArray(), addr).disclose()
        assertEquals("personal_sign", d.method)
        assertEquals(addr, d.signer)
        assertEquals("Sign in to dApp", d.details["message"])
    }

    @Test
    fun sendTransactionDisclosesRecipientAndValue() {
        val to = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
        val tx = Eip1559Transaction(
            chainId = 8453L,
            nonce = Quantity.of(1),
            maxPriorityFeePerGas = Quantity.of(1_000_000_000L),
            maxFeePerGas = Quantity.of(20_000_000_000L),
            gasLimit = Quantity.of(21_000L),
            to = to,
            value = Quantity.of(1_000L),
        )
        val d = WcSigningRequest.SendTransaction(addr, tx).disclose()
        assertEquals("eth_sendTransaction", d.method)
        assertEquals(to.value, d.details["to"])
        assertEquals("8453", d.details["chainId"])
        assertTrue(d.details.containsKey("data"))
    }
}
