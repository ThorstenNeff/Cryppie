package com.tneff.cyppie.send

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.walletcore.EvmChain

/** Send-flow failures, mapped to the spec's error/UX states. */
sealed class SendError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** `from` isn't one of the wallet's accounts — the wallet can't sign for it (Guardrail #3). */
    class AccountMismatch(val from: EvmAddress) :
        SendError("Address $from is not a wallet account")

    /** Chain not supported / no RPC configured for it. */
    class UnsupportedChain(val chain: EvmChain) :
        SendError("Unsupported chain ${chain.displayName}")

    /** Balance < value + worst-case network fee (checked before signing). */
    class InsufficientFunds(val balance: com.tneff.cyppie.evm.Quantity, val required: com.tneff.cyppie.evm.Quantity) :
        SendError("Insufficient funds: have $balance, need $required")

    /** L3 transport / all-providers-failed during completion or broadcast — retryable. */
    class NetworkError(message: String, cause: Throwable? = null) : SendError(message, cause)

    /** The node rejected the tx (revert / invalid) — authoritative, do NOT retry. */
    class TransactionRejected(message: String) : SendError(message)
}
