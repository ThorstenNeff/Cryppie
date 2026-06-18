package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress

/** A curated, well-known token (KAN-83 Stage 3) — for the Home asset list / add-token suggestions. */
data class CuratedToken(
    val address: EvmAddress,
    val chain: EvmChain,
    val symbol: String,
    val name: String,
    val decimals: Int,
) {
    /** The resolver-shaped view (drops the display [name]). */
    fun toErc20Token(): Erc20Token = Erc20Token(address, chain, symbol, decimals)
}

/**
 * Bundled, curated default token list for the MVP chains (Ethereum + Base). Embedded in-code (no
 * network fetch — supply-chain-safe for a non-custodial wallet, PRD-02); the source-of-truth is this
 * curated set, expandable later (a build-time JSON asset is a possible refactor). Addresses are
 * canonical EIP-55 (validated at construction by [EvmAddress.parse]).
 *
 * **Address audit (KAN-90, 2026-06-18):** every entry's address + symbol + decimals are cross-checked
 * against Etherscan / BaseScan and pinned in `TokenCatalogAuditTest` (a fixture audit + a resolve-vs-
 * fixture check). EIP-55 only catches case typos — the audit catches a typo to *another valid* address.
 *
 * **Base USDT — deliberately omitted:** there is no official native Tether on Base; the only USDT
 * (`0xfde4…`) is a *bridged* token that Tether explicitly disclaims ("not issued by, redeemable by, or
 * affiliated with Tether"). A curated default list shouldn't endorse it; users can still add it via
 * Add-by-Contract (KAN-49). Hence 4 Ethereum + 3 Base entries.
 */
object TokenCatalog {

    val tokens: List<CuratedToken> = listOf(
        // Ethereum (chainId 1) — addresses audited vs Etherscan (KAN-90).
        curated(EvmChain.ETHEREUM, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", "USDC", "USD Coin", 6),
        curated(EvmChain.ETHEREUM, "0xdAC17F958D2ee523a2206206994597C13D831ec7", "USDT", "Tether USD", 6),
        curated(EvmChain.ETHEREUM, "0x6B175474E89094C44Da98b954EedeAC495271d0F", "DAI", "Dai Stablecoin", 18),
        curated(EvmChain.ETHEREUM, "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", "WETH", "Wrapped Ether", 18),
        // Base (chainId 8453) — addresses audited vs BaseScan (KAN-90). No native Tether → no USDT.
        curated(EvmChain.BASE, "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", "USDC", "USD Coin", 6),
        curated(EvmChain.BASE, "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb", "DAI", "Dai Stablecoin", 18),
        curated(EvmChain.BASE, "0x4200000000000000000000000000000000000006", "WETH", "Wrapped Ether", 18),
    )

    /** Curated tokens for [chain]. */
    fun forChain(chain: EvmChain): List<CuratedToken> = tokens.filter { it.chain == chain }

    /** The curated entry for [address] on [chain], if it's a known token. */
    fun find(address: EvmAddress, chain: EvmChain): CuratedToken? =
        tokens.firstOrNull { it.chain == chain && it.address == address }
}

private fun curated(chain: EvmChain, address: String, symbol: String, name: String, decimals: Int) =
    CuratedToken(EvmAddress.parse(address), chain, symbol, name, decimals)
