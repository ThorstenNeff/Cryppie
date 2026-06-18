package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
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

        // Price each token; native ETH borrows its chain's WETH price.
        val priceTokens = holdings.mapNotNull { priceTokenFor(it.token) }.distinct()
        val rawPrices = if (priceTokens.isEmpty()) emptyMap() else priceSource.currentPrices(priceTokens, vs)
        val prices = holdings.mapNotNull { h ->
            priceTokenFor(h.token)?.let { pt -> rawPrices[pt]?.let { price -> h.token to price } }
        }.toMap()

        return PortfolioValuator.value(holdings, prices, config, clockEpochSeconds())
    }

    /** The token to price for [token] — itself for ERC-20, the chain's WETH for native ETH. */
    private fun priceTokenFor(token: PortfolioToken): PortfolioToken? =
        if (token.isNative) NATIVE[token.chainId]?.weth else token

    private fun RawTokenHolding.toHolding(): Holding? {
        val decimals = decimals ?: return null // can't value without decimals
        return Holding(
            account = holder,
            token = PortfolioToken(chainId, contract, symbol ?: "?", decimals),
            rawBalance = balance,
        )
    }

    private data class NativeMeta(val eth: PortfolioToken, val weth: PortfolioToken)

    private companion object {
        // Audited WETH addresses (KAN-90) — native ETH is valued at the WETH price.
        val NATIVE: Map<Long, NativeMeta> = mapOf(
            1L to NativeMeta(
                eth = PortfolioToken(1L, null, "ETH", 18),
                weth = PortfolioToken(1L, EvmAddress.parse("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"), "WETH", 18),
            ),
            8453L to NativeMeta(
                eth = PortfolioToken(8453L, null, "ETH", 18),
                weth = PortfolioToken(8453L, EvmAddress.parse("0x4200000000000000000000000000000000000006"), "WETH", 18),
            ),
        )
    }
}
