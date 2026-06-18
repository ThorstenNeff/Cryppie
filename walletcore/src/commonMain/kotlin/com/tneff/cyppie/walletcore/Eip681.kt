package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress

/** EIP-681 payment/receive URIs (the QR payload for the Receive screen). */
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
