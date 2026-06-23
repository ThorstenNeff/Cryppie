package com.tneff.cyppie.walletcore

/**
 * The EVM chains the wallet supports: Ethereum + Base (mainnet, PRD-02) and Base Sepolia (testnet, PRD-09 v1).
 * Safe to iterate `entries` only where the active [com.tneff.cyppie.evm.network.NetworkProfile.chainIds] gate
 * the work — the data layer iterates the active profile, not all entries (KAN-173).
 */
enum class EvmChain(val chainId: Long, val caip2: String, val displayName: String) {
    ETHEREUM(1L, "eip155:1", "Ethereum"),
    BASE(8453L, "eip155:8453", "Base"),
    BASE_SEPOLIA(84532L, "eip155:84532", "Base Sepolia"),
    ;

    companion object {
        fun fromChainId(chainId: Long): EvmChain? = entries.firstOrNull { it.chainId == chainId }
        fun fromCaip2(caip2: String): EvmChain? = entries.firstOrNull { it.caip2 == caip2 }
    }
}
