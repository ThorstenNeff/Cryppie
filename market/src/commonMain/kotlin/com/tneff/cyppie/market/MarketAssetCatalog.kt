package com.tneff.cyppie.market

import com.tneff.cyppie.evm.EvmAddress

/**
 * Routing-key catalog (PRD-04 / KAN-132) — resolves a [MarketAsset] to the per-source identifiers the
 * [BridgeMarketDataApi] needs. Keeps the curated-list **policy out of the routing logic** (data-driven,
 * swappable): a `null` means "this source can't serve this asset". The full PRD-08 service replaces the
 * static seed with a server-curated registry behind the same interface.
 */
interface MarketAssetCatalog {
    /** CoinGecko `asset_platform` for [chainId] (1→"ethereum", 8453→"base"); null if the chain is unsupported. */
    fun coinGeckoPlatform(chainId: Long): String?

    /** CoinGecko coin id for [asset] (native ETH→"ethereum", curated tokens→their id); null if unknown. */
    fun coinGeckoId(asset: MarketAsset): String?

    /** Binance trading-pair symbol for [asset] (e.g. native ETH→"ETHUSDT"); null when no major pair (candles only). */
    fun binanceSymbol(asset: MarketAsset): String?
}

/**
 * The MVP seed of [MarketAssetCatalog] (KAN-132), pinned from `catalog-mapping.md` — the KAN-90-audited
 * `TokenCatalog` plus the native coins. PO-confirmed (2026-06-19): native ETH gets Binance candles
 * (`ETHUSDT`); **WETH candles come from CoinGecko `weth`-id** (`binanceSymbol` = null) to avoid mislabeling
 * ETH candles as WETH; stablecoins are spot/history-only (`binanceSymbol` = null — flat candles aren't useful).
 * BTC is a display-only chart major (no [MarketAsset]) and is handled out of band by MD-3, not here.
 *
 * The table is audited in `MarketAssetCatalogTest` (like `TokenCatalogAuditTest`) so a wrong id/symbol is caught.
 */
object DefaultMarketAssetCatalog : MarketAssetCatalog {

    private val PLATFORMS: Map<Long, String> = mapOf(1L to "ethereum", 8453L to "base")

    // chainId → (lowercased contract → coin id) for the curated ERC-20 set.
    private val ERC20_IDS: Map<Long, Map<String, String>> = mapOf(
        1L to mapOf(
            "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" to "usd-coin", // USDC
            "0xdac17f958d2ee523a2206206994597c13d831ec7" to "tether",   // USDT
            "0x6b175474e89094c44da98b954eedeac495271d0f" to "dai",      // DAI
            "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2" to "weth",     // WETH
        ),
        8453L to mapOf(
            "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913" to "usd-coin", // USDC (Base)
            "0x50c5725949a6f0c72e6c4a641f24049a917db0cb" to "dai",      // DAI (Base)
            "0x4200000000000000000000000000000000000006" to "weth",    // WETH (Base)
        ),
    )

    override fun coinGeckoPlatform(chainId: Long): String? = PLATFORMS[chainId]

    override fun coinGeckoId(asset: MarketAsset): String? = when (asset) {
        is MarketAsset.Native -> if (asset.chainId in PLATFORMS) "ethereum" else null
        is MarketAsset.Erc20 -> ERC20_IDS[asset.chainId]?.get(asset.contract.hexLower())
    }

    // Only the native coin maps to a Binance pair (ETH≈ETHUSDT). Tokens (incl. WETH) → null: stablecoin
    // candles are flat/meaningless and WETH candles come from CoinGecko's weth-id (no ETH-as-WETH mislabel).
    override fun binanceSymbol(asset: MarketAsset): String? = when (asset) {
        is MarketAsset.Native -> if (asset.chainId in PLATFORMS) "ETHUSDT" else null
        is MarketAsset.Erc20 -> null
    }
}

/** Lowercased 0x-hex of an [EvmAddress] (EIP-55 `toString` → lowercase) for case-insensitive keying. */
internal fun EvmAddress.hexLower(): String = toString().lowercase()
