package com.tneff.cyppie.market

import com.tneff.cyppie.evm.EvmAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Audit-pin of the [DefaultMarketAssetCatalog] seed (KAN-132), like `TokenCatalogAuditTest`: a wrong
 * coin-id / platform / Binance symbol is a silent routing bug, so the whole table is asserted here.
 */
class MarketAssetCatalogTest {

    private val catalog = DefaultMarketAssetCatalog

    private fun erc20(chain: Long, addr: String) = MarketAsset.Erc20(chain, EvmAddress.parse(addr))

    @Test
    fun platforms() {
        assertEquals("ethereum", catalog.coinGeckoPlatform(1L))
        assertEquals("base", catalog.coinGeckoPlatform(8453L))
        assertNull(catalog.coinGeckoPlatform(10L)) // Optimism unsupported in MVP
    }

    @Test
    fun nativeEthMapsToEthereumIdAndEthusdt() {
        assertEquals("ethereum", catalog.coinGeckoId(MarketAsset.Native(1L)))
        assertEquals("ethereum", catalog.coinGeckoId(MarketAsset.Native(8453L)))
        assertEquals("ETHUSDT", catalog.binanceSymbol(MarketAsset.Native(1L)))
        assertEquals("ETHUSDT", catalog.binanceSymbol(MarketAsset.Native(8453L)))
    }

    @Test
    fun nativeOnUnknownChainIsUnresolved() {
        assertNull(catalog.coinGeckoId(MarketAsset.Native(10L)))
        assertNull(catalog.binanceSymbol(MarketAsset.Native(10L)))
    }

    @Test
    fun ethereumTokenCoinIds() {
        assertEquals("usd-coin", catalog.coinGeckoId(erc20(1L, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"))) // USDC
        assertEquals("tether", catalog.coinGeckoId(erc20(1L, "0xdAC17F958D2ee523a2206206994597C13D831ec7"))) // USDT
        assertEquals("dai", catalog.coinGeckoId(erc20(1L, "0x6B175474E89094C44Da98b954EedeAC495271d0F"))) // DAI
        assertEquals("weth", catalog.coinGeckoId(erc20(1L, "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))) // WETH
    }

    @Test
    fun baseTokenCoinIds() {
        assertEquals("usd-coin", catalog.coinGeckoId(erc20(8453L, "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913")))
        assertEquals("dai", catalog.coinGeckoId(erc20(8453L, "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb")))
        assertEquals("weth", catalog.coinGeckoId(erc20(8453L, "0x4200000000000000000000000000000000000006")))
    }

    @Test
    fun tokensHaveNoBinanceSymbol() {
        // Stablecoins (flat candles) + WETH (candles via CoinGecko weth-id, no ETH→WETH mislabel) → null.
        assertNull(catalog.binanceSymbol(erc20(1L, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"))) // USDC
        assertNull(catalog.binanceSymbol(erc20(1L, "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))) // WETH
    }

    @Test
    fun unknownTokenIsUnresolved() {
        val unknown = erc20(1L, "0x1111111111111111111111111111111111111111")
        assertNull(catalog.coinGeckoId(unknown))
        assertNull(catalog.binanceSymbol(unknown))
    }

    @Test
    fun coinIdKeyingIsChecksumInsensitive() {
        // Same USDC contract, all-lower vs EIP-55 mixed-case → identical resolution (keyed by lowercase hex).
        val lower = erc20(1L, "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val checksummed = erc20(1L, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        assertEquals(catalog.coinGeckoId(lower), catalog.coinGeckoId(checksummed))
        assertEquals("usd-coin", catalog.coinGeckoId(lower))
    }
}
