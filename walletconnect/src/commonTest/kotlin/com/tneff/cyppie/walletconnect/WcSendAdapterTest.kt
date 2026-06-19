package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.walletcore.EvmChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** KAN-62 — WC `eth_sendTransaction` → `SendInput` mapping + guardrails (fill-not-override, fail-closed). */
class WcSendAdapterTest {

    private val from = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val to = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")

    private fun params(
        chainId: String = "eip155:1",
        to: EvmAddress? = this.to,
        gasLimit: Quantity? = null,
        maxFeePerGas: Quantity? = null,
        maxPriorityFeePerGas: Quantity? = null,
        nonce: Quantity? = null,
    ) = SendTransactionParams(
        chainId = chainId,
        from = from,
        to = to,
        value = Quantity.of(1_000),
        data = ByteArray(0),
        gasLimit = gasLimit,
        maxFeePerGas = maxFeePerGas,
        maxPriorityFeePerGas = maxPriorityFeePerGas,
        nonce = nonce,
    )

    @Test
    fun mapsCaip2ToSupportedChain() {
        assertEquals(EvmChain.ETHEREUM, WcSendAdapter.toSendInput(params(chainId = "eip155:1")).chain)
        assertEquals(EvmChain.BASE, WcSendAdapter.toSendInput(params(chainId = "eip155:8453")).chain)
    }

    @Test
    fun preservesDappFieldsAndLeavesMissingNull() {
        // #2 fill-not-override: a dApp-pinned gas/fee is carried through; absent fields stay null for L3.
        val input = WcSendAdapter.toSendInput(
            params(gasLimit = Quantity.of(21_000), maxFeePerGas = Quantity.of(50)),
        )
        assertEquals(from, input.from)
        assertEquals(to, input.to)
        assertEquals(Quantity.of(1_000), input.value)
        assertEquals(Quantity.of(21_000), input.gasLimit)
        assertEquals(Quantity.of(50), input.maxFeePerGas)
        assertNull(input.maxPriorityFeePerGas) // not supplied → orchestrator fills
        assertNull(input.nonce)
    }

    @Test
    fun unsupportedChainIsRejected() {
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            WcSendAdapter.toSendInput(params(chainId = "eip155:137")) // Polygon — not supported
        }
    }

    @Test
    fun nonEip155NamespaceIsRejected() {
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            WcSendAdapter.toSendInput(params(chainId = "cosmos:cosmoshub-4"))
        }
    }

    @Test
    fun chainOutsideApprovedSessionIsRejected() {
        // #4 chain binding: ETH request but only Base approved → reject (replay/scope guard).
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            WcSendAdapter.toSendInput(params(chainId = "eip155:1"), approvedChainIds = setOf(8453L))
        }
    }

    @Test
    fun approvedChainIsAccepted() {
        val input = WcSendAdapter.toSendInput(params(chainId = "eip155:1"), approvedChainIds = setOf(1L, 8453L))
        assertEquals(EvmChain.ETHEREUM, input.chain)
    }

    @Test
    fun contractCreationWithoutRecipientIsRejected() {
        assertFailsWith<WalletConnectException.UnsupportedRequest> {
            WcSendAdapter.toSendInput(params(to = null))
        }
    }
}
