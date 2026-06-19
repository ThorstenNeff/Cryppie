package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.market.FiatPricePoint

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.rpc.AssetTransfer
import com.tneff.cyppie.rpc.TransferCategory
import com.tneff.cyppie.rpc.TransferDirection
import com.tneff.cyppie.rpc.TransferPage
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * End-to-end **rich** (P4) assembler (KAN-100/102): pulls an account's **full** transfer history and
 * turns per-token transfers + a price history into FIFO cost-basis and a value-over-time series. ISO
 * timestamps → epoch via the stdlib `kotlin.time.Instant` (Kotlin 2.4 — no extra dependency).
 */
@OptIn(ExperimentalTime::class)
object RichPortfolio {

    /** A fetched history + whether the page cap was hit (more transfers exist — [truncated]). */
    data class AccountHistory(val transfers: List<AssetTransfer>, val truncated: Boolean)

    /**
     * Full transfer history for [address] — **both directions, all pages** (L2: a single page would
     * silently truncate cost-basis / series). [fetchTransfers] returns one page; this loops `pageKey`.
     * If [maxPages] is hit while pages remain, [AccountHistory.truncated] is set so callers can flag the
     * result `INCOMPLETE_TRANSFERS` (no silent truncation for whales).
     */
    suspend fun fullHistory(
        address: EvmAddress,
        fetchTransfers: suspend (address: EvmAddress, direction: TransferDirection, pageKey: String?) -> TransferPage,
        maxPages: Int = 50,
    ): AccountHistory {
        val all = mutableListOf<AssetTransfer>()
        var truncated = false
        for (direction in TransferDirection.entries) {
            var pageKey: String? = null
            var pages = 0
            do {
                val page = fetchTransfers(address, direction, pageKey)
                all += page.transfers
                pageKey = page.nextPageKey
                pages++
                if (pageKey != null && pages >= maxPages) {
                    truncated = true // more pages exist but the cap was hit
                    break
                }
            } while (pageKey != null)
        }
        return AccountHistory(all, truncated)
    }

    /** The price at-or-before [epoch] in a price [history] (nearest prior; first point if all later). */
    fun priceAt(history: List<FiatPricePoint>, epoch: Long): Money? {
        if (history.isEmpty()) return null
        val sorted = history.sortedBy { it.epochSeconds }
        var chosen: Money? = null
        for (p in sorted) {
            if (p.epochSeconds <= epoch) chosen = p.price else break
        }
        return chosen ?: sorted.first().price
    }

    /**
     * FIFO cost-basis for [token] held by [account], pricing each transfer at its block time. Pass
     * [truncated] = `AccountHistory.truncated` so a page-capped history forces `INCOMPLETE_TRANSFERS`
     * (the result is then visibly an approximation, never silently truncated — L1).
     */
    fun tokenCostBasis(
        token: PortfolioToken,
        account: EvmAddress,
        transfers: List<AssetTransfer>,
        history: List<FiatPricePoint>,
        truncated: Boolean = false,
    ): CostBasisEngine.CostBasis {
        val fallbackCurrency = history.firstOrNull()?.price?.currency ?: "USD"
        val events = tokenEvents(transfers, token, account).map { (epoch, received, amount) ->
            FifoEvent(received, amount, priceAt(history, epoch) ?: Money.zero(fallbackCurrency, Valuation.PRICE_SCALE))
        }
        val result = CostBasisEngine.fifo(token.decimals, events)
        return if (truncated && ApproxReason.INCOMPLETE_TRANSFERS !in result.reasons) {
            result.copy(reasons = result.reasons + ApproxReason.INCOMPLETE_TRANSFERS)
        } else {
            result
        }
    }

    /** Value-over-time series for [token] held by [account] across the price [history]. */
    fun tokenValueSeries(
        token: PortfolioToken,
        account: EvmAddress,
        transfers: List<AssetTransfer>,
        history: List<FiatPricePoint>,
    ): List<ValuePoint> {
        val timed = tokenEvents(transfers, token, account).map { (epoch, received, amount) -> TimedAmount(epoch, received, amount) }
        return PortfolioTimeSeries.tokenValueSeries(token.decimals, timed, history)
    }

    /** This [token]'s transfers for [account], as time-ordered (epoch, received, amount). */
    private fun tokenEvents(
        transfers: List<AssetTransfer>,
        token: PortfolioToken,
        account: EvmAddress,
    ): List<Triple<Long, Boolean, Quantity>> = transfers.mapNotNull { t ->
        val matchesToken = if (token.isNative) {
            t.contract == null && (t.category == TransferCategory.EXTERNAL || t.category == TransferCategory.INTERNAL)
        } else {
            t.contract == token.contract
        }
        if (!matchesToken) return@mapNotNull null
        val amount = t.rawValue ?: return@mapNotNull null
        val epoch = t.blockTimestampIso?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() } ?: return@mapNotNull null
        val received = t.to == account
        val sent = t.from == account
        when {
            received && !sent -> Triple(epoch, true, amount)
            sent && !received -> Triple(epoch, false, amount)
            else -> null // self-transfer / unrelated → ignore
        }
    }.sortedBy { it.first }
}
