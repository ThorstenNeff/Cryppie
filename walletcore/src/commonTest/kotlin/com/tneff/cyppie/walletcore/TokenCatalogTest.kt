package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenCatalogTest {

    @Test
    fun loadsCuratedTokensForBothChains() {
        // Also proves every bundled address is canonical EIP-55 (parse would throw at init otherwise).
        assertTrue(TokenCatalog.forChain(EvmChain.ETHEREUM).isNotEmpty())
        assertTrue(TokenCatalog.forChain(EvmChain.BASE).isNotEmpty())
        assertTrue(TokenCatalog.forChain(EvmChain.ETHEREUM).all { it.chain == EvmChain.ETHEREUM })
        assertTrue(TokenCatalog.tokens.all { it.decimals in 0..18 && it.symbol.isNotBlank() })
    }

    @Test
    fun findsKnownTokenByAddressAndChain() {
        val usdcEth = EvmAddress.parse("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        val found = TokenCatalog.find(usdcEth, EvmChain.ETHEREUM)
        assertEquals("USDC", found?.symbol)
        assertEquals(6, found?.decimals)
        assertNull(TokenCatalog.find(usdcEth, EvmChain.BASE)) // chain-scoped
    }
}
