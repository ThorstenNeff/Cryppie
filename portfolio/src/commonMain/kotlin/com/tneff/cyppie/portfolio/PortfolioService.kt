package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.market.PriceSource
import com.tneff.cyppie.rpc.RawTokenHolding

/**
 * End-to-end portfolio assembler (KAN-100): fetch ERC-20 holdings (Alchemy Data API) + native balance
 * (L3) across accounts × chains, price them, and value into a [Portfolio] via [PortfolioValuator].
 * Native ETH is priced via its chain's **WETH** token (ETH ≈ WETH). Dependencies are injected as
 * functions / the [PriceSource] interface so the assembler is unit-testable without live network and
 * stays web-capable (`:rpc`+`:evm` only).
 */
class PortfolioService(
    private val fetchErc20Holdings: suspend (accounts: List<EvmAddress>, chainIds: List<Long>) -> List<RawTokenHolding>,
    private val fetchNativeBalance: suspend (chainId: Long, account: EvmAddress) -> Quantity,
    private val priceSource: PriceSource,
    private val config: PortfolioConfig,
    private val clockEpochSeconds: () -> Long,
) {

    suspend fun load(accounts: List<EvmAddress>, chainIds: List<Long>, vs: String): Portfolio {
        val erc20 = fetchErc20Holdings(accounts, chainIds).mapNotNull { it.toHolding() }
        val native = accounts.flatMap { account ->
            chainIds.mapNotNull { chainId ->
                NATIVE[chainId]?.let { meta -> Holding(account, meta.eth, fetchNativeBalance(chainId, account)) }
            }
        }
        val holdings = erc20 + native

        // Price each token; native ETH borrows its chain's WETH price. Adapter boundary (ADR-0025 Phase 2):
        // convert price tokens → :market's MarketAsset, call the relocated PriceSource, then re-key the result
        // back to PortfolioToken so the valuator/consumers stay on PortfolioToken (no consumer churn).
        val priceTokens = holdings.mapNotNull { priceTokenFor(it.token) }.distinct()
        val rawPrices = if (priceTokens.isEmpty()) {
            emptyMap()
        } else {
            priceSource.currentPrices(priceTokens.map { it.toMarketAsset() }.distinct(), vs)
        }
        val prices = holdings.mapNotNull { h ->
            priceTokenFor(h.token)?.let { pt -> rawPrices[pt.toMarketAsset()]?.let { price -> h.token to price } }
        }.toMap()

        return PortfolioValuator.value(holdings, prices, config, clockEpochSeconds())
    }

    private fun RawTokenHolding.toHolding(): Holding? {
        val decimals = decimals ?: return null // can't value without decimals
        return Holding(
            account = holder,
            token = PortfolioToken(chainId, contract, symbol ?: "?", decimals),
            rawBalance = balance,
        )
    }

    private data class NativeMeta(val eth: PortfolioToken, val weth: PortfolioToken)

    companion object {
        /**
         * The single source of truth for "what token to price for [token]" — itself for an ERC-20, the
         * chain's audited WETH for native ETH (ETH ≈ WETH; null if the chain is unknown). Used by both
         * the live valuation here AND the P&L/24h assembler (KAN-124) so native ETH is priced against the
         * SAME WETH everywhere — no second address table to drift (review M1).
         */
        fun priceTokenFor(token: PortfolioToken): PortfolioToken? =
            if (token.isNative) NATIVE[token.chainId]?.weth else token

        // Native ETH is valued at the chain's WETH price (KAN-90). WETH addresses come from the ADR-0027
        // NetworkProfiles registry (single-source, no per-table drift) — chains without a sourced WETH (e.g.
        // Sepolia) get no native pricing entry and degrade cleanly. Mainnet 1/8453 values are unchanged.
        private val NATIVE: Map<Long, NativeMeta> =
            com.tneff.cyppie.evm.network.NetworkProfiles.all
                .flatMap { it.chains }
                .filter { it.wrappedNative != null }
                .associate { c ->
                    c.chainId to NativeMeta(
                        eth = PortfolioToken(c.chainId, null, "ETH", 18),
                        weth = PortfolioToken(c.chainId, EvmAddress.parse(c.wrappedNative!!), "WETH", 18),
                    )
                }
    }
}
