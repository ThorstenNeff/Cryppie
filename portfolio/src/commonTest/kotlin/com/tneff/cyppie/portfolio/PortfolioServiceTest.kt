package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.rpc.RawTokenHolding
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortfolioServiceTest {

    private val account = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val usdcContract = EvmAddress.parse("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
    private val usdc = PortfolioToken(1L, usdcContract, "USDC", 6)
    private val weth = PortfolioToken(1L, EvmAddress.parse("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"), "WETH", 18)

    private fun wholeEth(n: Long): Quantity {
        var v = Quantity.of(n)
        repeat(18) { v *= Quantity.of(10) }
        return v
    }

    @Test
    fun assemblesNativeAndErc20AndValuesNativeViaWeth() = runTest {
        val service = PortfolioService(
            fetchErc20Holdings = { _, _ -> listOf(RawTokenHolding(account, 1L, usdcContract, Quantity.of(2_000_000), "USDC", 6)) },
            fetchNativeBalance = { _, _ -> wholeEth(1) }, // 1 ETH
            priceSource = object : PriceSource {
                override suspend fun currentPrices(tokens: List<PortfolioToken>, vs: String) = mapOf(
                    usdc to TokenPrice(Money(100_000_000, 8, "USD"), asOfEpochSeconds = 1_000), // $1.00
                    weth to TokenPrice(Money(250_000_000_000, 8, "USD"), asOfEpochSeconds = 1_000), // $2,500
                )
            },
            config = PortfolioConfig(knownGood = emptySet(), currency = "USD", dustThresholdCents = 1),
            clockEpochSeconds = { 1_000 },
        )

        val pf = service.load(listOf(account), listOf(1L), "USD")

        // USDC $2 + native ETH $2500 (priced via WETH) = $2502.00
        assertEquals(Money(250_200, 2, "USD"), pf.totalValue!!.value)
        val ethHolding = pf.holdings.first { it.token.isNative }
        assertEquals(Money(250_000, 2, "USD"), ethHolding.value) // 1 ETH × $2500
        assertTrue(pf.holdings.any { it.token.symbol == "USDC" })
        assertEquals(10_000, pf.allocation.sumOf { it.fractionBps })
    }
}
