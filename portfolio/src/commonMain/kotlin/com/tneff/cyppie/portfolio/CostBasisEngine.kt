package com.tneff.cyppie.portfolio

import com.tneff.cyppie.market.Money

import com.tneff.cyppie.evm.Quantity

/**
 * One per-token FIFO event: an acquisition ([received] = true) or disposal of [amount] base units at
 * [unitPrice] (fiat price per whole token, scale-8 `Money`) as of the event time.
 */
data class FifoEvent(val received: Boolean, val amount: Quantity, val unitPrice: Money)

/**
 * FIFO cost-basis reconstruction (KAN-102 Slice P4). Replays a token's **time-ordered** [FifoEvent]s
 * to derive the current cost basis + realized P&L (cents). Deterministic / float-free (FR-6). It is a
 * best-effort approximation (FR-9): when more is disposed than was tracked (wallet predates tracking /
 * gapped history) the untracked portion is treated as zero-cost and the result carries
 * [ApproxReason.INCOMPLETE_TRANSFERS] + [ApproxReason.WALLET_PREDATES_TRACKING].
 */
object CostBasisEngine {

    data class CostBasis(
        val currentAmount: Quantity,
        val costBasisCents: Long,
        val realizedPnlCents: Long,
        val reasons: List<ApproxReason>,
    )

    private class Lot(var amount: Quantity, val unitPrice: Money)

    fun fifo(decimals: Int, events: List<FifoEvent>): CostBasis {
        val lots = ArrayDeque<Lot>()
        var realized = 0L
        val reasons = linkedSetOf(ApproxReason.COST_BASIS_AMBIGUITY)

        for (e in events) {
            if (e.received) {
                if (!e.amount.isZero) lots.addLast(Lot(e.amount, e.unitPrice))
                continue
            }
            var remaining = e.amount
            while (remaining > Quantity.ZERO && lots.isNotEmpty()) {
                val lot = lots.first()
                val consume = if (lot.amount <= remaining) lot.amount else remaining
                // Realized = proceeds − cost on the consumed units (saturating, KAN-102 L1 family).
                realized = saturatingAdd(
                    realized,
                    saturatingSub(
                        Valuation.valueCents(consume, decimals, e.unitPrice),
                        Valuation.valueCents(consume, decimals, lot.unitPrice),
                    ),
                )
                lot.amount = lot.amount - consume
                remaining = remaining - consume
                if (lot.amount.isZero) lots.removeFirst()
            }
            if (remaining > Quantity.ZERO) {
                // Disposed more than tracked — untracked lots have unknown (treated zero) cost basis.
                reasons += ApproxReason.INCOMPLETE_TRANSFERS
                reasons += ApproxReason.WALLET_PREDATES_TRACKING
                realized = saturatingAdd(realized, Valuation.valueCents(remaining, decimals, e.unitPrice))
            }
        }

        val currentAmount = lots.fold(Quantity.ZERO) { acc, l -> acc + l.amount }
        val costBasis = lots.fold(0L) { acc, l -> saturatingAdd(acc, Valuation.valueCents(l.amount, decimals, l.unitPrice)) }
        return CostBasis(currentAmount, costBasis, realized, reasons.toList())
    }

    private fun saturatingAdd(a: Long, b: Long): Long {
        val r = a + b
        return if ((a xor r) and (b xor r) < 0L) (if (a > 0L) Long.MAX_VALUE else Long.MIN_VALUE) else r
    }

    private fun saturatingSub(a: Long, b: Long): Long {
        val r = a - b
        return if ((a xor b) and (a xor r) < 0L) (if (a >= 0L) Long.MAX_VALUE else Long.MIN_VALUE) else r
    }
}
