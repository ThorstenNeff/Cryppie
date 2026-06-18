package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress

/**
 * EIP-681 payment/receive URIs. Note (KAN-78 L2): the Receive screen's QR encodes the **bare
 * address** (not [receiveUri]) for maximum scanner compatibility, since the address is chain-identical
 * for Ethereum and Base. These builders are retained for **amount/value requests** ([valueRequestUri])
 * — a "request N tokens" QR — which is the planned next use; keep, don't remove.
 */
object Eip681 {

    /** Plain receive target: `ethereum:<address>@<chainId>` (chain-scoped, no amount). */
    fun receiveUri(address: EvmAddress, chain: EvmChain): String =
        "ethereum:${address.value}@${chain.chainId}"

    /**
     * Native-value request: `ethereum:<address>@<chainId>?value=<wei>`. ERC-20 transfer requests
     * (`/transfer?address=…&uint256=…`) are added with the Send/token work.
     */
    fun valueRequestUri(address: EvmAddress, chain: EvmChain, valueWei: String): String =
        "${receiveUri(address, chain)}?value=$valueWei"
}
