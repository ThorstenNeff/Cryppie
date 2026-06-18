package com.tneff.cyppie.walletcore

/** The EVM chains the MVP wallet supports (PRD-02): Ethereum + Base. */
enum class EvmChain(val chainId: Long, val caip2: String, val displayName: String) {
    ETHEREUM(1L, "eip155:1", "Ethereum"),
    BASE(8453L, "eip155:8453", "Base"),
    ;

    companion object {
        fun fromChainId(chainId: Long): EvmChain? = entries.firstOrNull { it.chainId == chainId }
        fun fromCaip2(caip2: String): EvmChain? = entries.firstOrNull { it.caip2 == caip2 }
    }
}
