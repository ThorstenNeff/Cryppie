package com.tneff.cyppie.portfolio

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortfolioValuatorTest {

    private val account = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val usdc = PortfolioToken(1L, EvmAddress.parse("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"), "USDC", 6)
    private val weth = PortfolioToken(1L, EvmAddress.parse("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"), "WETH", 18)
    private val scam = PortfolioToken(1L, EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"), "SCAM", 18)

    private val config = PortfolioConfig(
        knownGood = setOf(TokenKey(1L, usdc.contract), TokenKey(1L, weth.contract)),
        currency = "USD",
        dustThresholdCents = 1,
    )

    private fun wholeEth(n: Long): Quantity {
        var v = Quantity.of(n)
        repeat(18) { v *= Quantity.of(10) }
        return v
    }

    @Test
    fun totalValueAndAllocationSumTo100Percent() {
        val holdings = listOf(
            Holding(account, usdc, Quantity.of(2_000_000)), // 2 USDC
            Holding(account, weth, wholeEth(1)), // 1 WETH
        )
        val prices = mapOf(
            usdc to TokenPrice(Money(100_000_000, 8, "USD"), asOfEpochSeconds = 1_000), // $1.00
            weth to TokenPrice(Money(250_000_000_000, 8, "USD"), asOfEpochSeconds = 1_000), // $2,500.00
        )
        val pf = PortfolioValuator.value(holdings, prices, config, nowEpochSeconds = 1_000)

        assertEquals(Money(250_200, 2, "USD"), pf.totalValue!!.value) // $2 + $2500 = $2502.00
        assertFalse(pf.totalValue!!.isApproximate)
        assertEquals(10_000, pf.allocation.sumOf { it.fractionBps }) // FR-6: allocation = 100%
        assertEquals(weth, pf.allocation.first().token) // largest slice first
    }

    @Test
    fun stalePriceMarksTotalApproximate() {
        val holdings = listOf(Holding(account, usdc, Quantity.of(2_000_000)))
        val prices = mapOf(usdc to TokenPrice(Money(100_000_000, 8, "USD"), asOfEpochSeconds = 1_000))
        val pf = PortfolioValuator.value(holdings, prices, config, nowEpochSeconds = 1_100) // 100s > 60s TTL

        assertTrue(pf.totalValue!!.isApproximate)
        assertEquals(listOf(ApproxReason.STALE_PRICES), pf.totalValue!!.reasons)
    }

    @Test
    fun filtersDustNonCatalogButKeepsKnownGood() {
        val holdings = listOf(
            Holding(account, scam, wholeEth(1)), // not known-good, unpriced → 0 value → dust → filtered
            Holding(account, usdc, Quantity.ZERO), // known-good → kept even at 0 value
        )
        val pf = PortfolioValuator.value(holdings, emptyMap(), config, nowEpochSeconds = 1_000)
        assertEquals(listOf(usdc), pf.holdings.map { it.token })
    }
}
