package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.FiatPricePoint

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.rpc.AssetTransfer
import com.tneff.cyppie.rpc.TransferCategory
import com.tneff.cyppie.rpc.TransferDirection
import com.tneff.cyppie.rpc.TransferPage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RichPortfolioTest {

    private val account = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val other = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
    private val weth = PortfolioToken(1L, EvmAddress.parse("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"), "WETH", 18)

    private fun eth(n: Long): Quantity {
        var v = Quantity.of(n)
        repeat(18) { v *= Quantity.of(10) }
        return v
    }

    private fun usd(dollars: Long) = Money(dollars * 100_000_000, 8, "USD")
    private fun isoAt(epoch: Long) = Instant.fromEpochSeconds(epoch).toString()

    private fun transfer(epoch: Long, from: EvmAddress, to: EvmAddress, amount: Quantity) = AssetTransfer(
        chainId = 1L, txHash = "0xhash", blockTimestampIso = isoAt(epoch),
        from = from, to = to, category = TransferCategory.ERC20, asset = "WETH",
        contract = weth.contract, rawValue = amount, decimals = 18,
    )

    @Test
    fun fullHistoryLoopsBothDirectionsAndAllPages() = runTest {
        val fetch: suspend (EvmAddress, TransferDirection, String?) -> TransferPage = { _, dir, pageKey ->
            when {
                dir == TransferDirection.SENT -> TransferPage(listOf(transfer(300, account, other, eth(1))), null)
                pageKey == null -> TransferPage(listOf(transfer(100, other, account, eth(2))), "P2")
                else -> TransferPage(listOf(transfer(200, other, account, eth(1))), null)
            }
        }
        val all = RichPortfolio.fullHistory(account, fetch)
        assertEquals(3, all.transfers.size) // 1 sent + 2 received pages — none truncated
        assertFalse(all.truncated)
    }

    @Test
    fun fullHistoryFlagsTruncationAtPageCapAndForcesIncomplete() = runTest {
        // fetch always returns a non-null pageKey → the page cap is hit (whale wallet).
        val fetch: suspend (EvmAddress, TransferDirection, String?) -> TransferPage =
            { _, _, _ -> TransferPage(listOf(transfer(100, other, account, eth(1))), "MORE") }
        val history = RichPortfolio.fullHistory(account, fetch, maxPages = 3)
        assertTrue(history.truncated)

        // Even with no over-disposal, a truncated history must read as approximate (no silent cut).
        val cb = RichPortfolio.tokenCostBasis(
            weth, account, history.transfers, listOf(FiatPricePoint(50, usd(100))), truncated = history.truncated,
        )
        assertTrue(ApproxReason.INCOMPLETE_TRANSFERS in cb.reasons)
    }

    @Test
    fun costBasisPricesEachTransferAtItsBlockTime() {
        val transfers = listOf(
            transfer(100, other, account, eth(2)), // acquire 2 @ t=100
            transfer(300, account, other, eth(2)), // dispose 2 @ t=300
        )
        val history = listOf(FiatPricePoint(50, usd(100)), FiatPricePoint(300, usd(300)))
        val cb = RichPortfolio.tokenCostBasis(weth, account, transfers, history)
        assertEquals(40_000L, cb.realizedPnlCents) // (300−100) × 2 = $400
        assertEquals(Quantity.ZERO, cb.currentAmount)
    }

    @Test
    fun priceAtReturnsNearestPriorSample() {
        val history = listOf(FiatPricePoint(50, usd(100)), FiatPricePoint(300, usd(300)))
        assertEquals(usd(100), RichPortfolio.priceAt(history, 100))
        assertEquals(usd(300), RichPortfolio.priceAt(history, 500))
        assertEquals(usd(100), RichPortfolio.priceAt(history, 10)) // before first → first
    }
}
