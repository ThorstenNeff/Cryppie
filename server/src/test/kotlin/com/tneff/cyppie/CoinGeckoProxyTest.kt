package com.tneff.cyppie

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** KAN — CoinGecko proxy least-privilege allow-list + server-side key injection (PRD-04). */
class CoinGeckoProxyTest {

    @Test
    fun allowsTheSupportedEndpointShapes() {
        assertTrue(isAllowedCoinGeckoTail("simple/token_price/ethereum"))
        assertTrue(isAllowedCoinGeckoTail("simple/token_price/base"))
        assertTrue(isAllowedCoinGeckoTail("simple/price"))
        assertTrue(isAllowedCoinGeckoTail("coins/ethereum/contract/0x1111111111111111111111111111111111111111/market_chart"))
        assertTrue(isAllowedCoinGeckoTail("coins/base/contract/0xaBcdef0000000000000000000000000000000001/market_chart/range"))
        assertTrue(isAllowedCoinGeckoTail("coins/bitcoin/ohlc"))
    }

    @Test
    fun rejectsUnsupportedOrWrongShapedPaths() {
        assertFalse(isAllowedCoinGeckoTail("simple/token_price/solana")) // platform not supported
        assertFalse(isAllowedCoinGeckoTail("coins/ethereum/contract/not-an-address/market_chart")) // bad address
        assertFalse(isAllowedCoinGeckoTail("coins/ethereum/contract/0x1111111111111111111111111111111111111111/tickers")) // wrong leaf
        assertFalse(isAllowedCoinGeckoTail("coins/bitcoin/market_chart/range/extra")) // not an allowed shape
        assertFalse(isAllowedCoinGeckoTail("onchain/networks/eth/pools")) // arbitrary path
        assertFalse(isAllowedCoinGeckoTail(""))
    }

    @Test
    fun injectsKeyAsServerSideQueryParam() {
        // The Pro key is appended server-side; the client never carries it. The proxy then appends the
        // client's own query (vs_currency/from/to) onto this URL faithfully.
        assertEquals(
            "https://pro-api.coingecko.com/api/v3/coins/bitcoin/ohlc?x_cg_pro_api_key=SECRET",
            CoinGeckoUpstream.url("SECRET", "coins/bitcoin/ohlc"),
        )
    }
}
