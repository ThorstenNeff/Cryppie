package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.send.PreparedSend
import com.tneff.cyppie.send.SendInput
import com.tneff.cyppie.send.SendOrchestrator
import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.walletcore.EvmChain

/**
 * Bridges a decoded WalletConnect `eth_sendTransaction` ([SendTransactionParams]) into the **one**
 * KAN-91 [SendOrchestrator] (ADR-0019) — so a dApp request and an in-app send share the exact same
 * completion, validation, disclosure and signing path. `:send` stays free of any WC type; the bridge
 * lives here.
 *
 * Guardrails (ADR-0015 / FR-3), fail-closed:
 * - **#2 fill-not-override** — every dApp-supplied field is carried into [SendInput] as-is; the
 *   orchestrator completes only the `null` fields (nonce/fees/gas). The signed tx equals the disclosed.
 * - **#3 account binding (by-construction)** — the dApp-supplied `from` must be one of the
 *   **session-approved accounts** ([approvedAccounts]), not merely *any* wallet account. Without this a
 *   dApp could name a different wallet account (one the user never approved for the session) as `from`;
 *   `WalletConnectSigner.requireSignerMatches` alone only proves the signer derives to `from`, not that
 *   the session authorized it. Fail-closed: an empty approved set rejects everything. The orchestrator
 *   then *also* re-checks `from` against the wallet accounts when resolving the signing index.
 * - **#4 chain binding (replay)** — the CAIP-2 chain must resolve to a supported [EvmChain] **and**, if
 *   [approvedChainIds] is non-empty, be one the session approved; else [WalletConnectException].
 * - recipient required — a `to`-less tx (contract creation) is **unsupported** via WC send (fail-closed).
 */
object WcSendAdapter {

    /**
     * Maps [params] to the orchestrator's [SendInput], binding `from` to the session-approved set.
     * [approvedAccounts] are the addresses the user approved for this WC session (account binding, #3).
     * [approvedChainIds] are the session-approved chain ids (chain binding, #4); empty means "any supported".
     *
     * @throws WalletConnectException.UnsupportedRequest on an unapproved sender/chain or a missing recipient.
     */
    fun toSendInput(
        params: SendTransactionParams,
        approvedAccounts: Set<EvmAddress>,
        approvedChainIds: Set<Long> = emptySet(),
    ): SendInput {
        // #3 account binding — the request's `from` must be an account the user approved for this session.
        if (params.from !in approvedAccounts) {
            throw WalletConnectException.UnsupportedRequest(
                "Sender ${params.from} is not an approved account for this WalletConnect session",
            )
        }
        val chain = resolveChain(params.chainId)
        if (approvedChainIds.isNotEmpty() && chain.chainId !in approvedChainIds) {
            throw WalletConnectException.UnsupportedRequest(
                "Chain ${chain.chainId} is not in the approved session chains $approvedChainIds",
            )
        }
        val to = params.to ?: throw WalletConnectException.UnsupportedRequest(
            "eth_sendTransaction without a recipient (contract creation) is unsupported",
        )
        return SendInput(
            from = params.from,
            to = to,
            value = params.value,
            data = params.data,
            chain = chain,
            // #2 fill-not-override: pass the dApp's values through untouched; orchestrator fills only nulls.
            nonce = params.nonce,
            gasLimit = params.gasLimit,
            maxFeePerGas = params.maxFeePerGas,
            maxPriorityFeePerGas = params.maxPriorityFeePerGas,
        )
    }

    /** Resolves a CAIP-2 chain ref (`eip155:1`) — or a bare/`eip155:`-prefixed id — to a supported [EvmChain]. */
    private fun resolveChain(caip2: String): EvmChain {
        EvmChain.entries.firstOrNull { it.caip2.equals(caip2, ignoreCase = true) }?.let { return it }
        val id = chainIdOrNull(caip2)
        return id?.let { EvmChain.fromChainId(it) }
            ?: throw WalletConnectException.UnsupportedRequest("Unsupported WalletConnect chain: $caip2")
    }

    private fun chainIdOrNull(caip2: String): Long? {
        if (':' !in caip2) return caip2.toLongOrNull()
        val (namespace, reference) = caip2.split(':', limit = 2)
        return if (namespace.equals("eip155", ignoreCase = true)) reference.toLongOrNull() else null
    }
}

/**
 * Convenience wiring (steps 1–4): decode-side [params] → [SendInput] → [SendOrchestrator.prepare].
 * Returns the [PreparedSend] whose `disclosure` the approval UI shows; signing happens afterwards via
 * [SendOrchestrator.signAndBroadcast] over the unlocked seed (post-approval, no fee filled after — #1).
 * Kept as an extension so `:send` carries no WC dependency.
 */
suspend fun SendOrchestrator.prepareWalletConnectSend(
    params: SendTransactionParams,
    approvedAccounts: List<EvmAccount>,
    approvedChainIds: Set<Long> = emptySet(),
): PreparedSend {
    // The session-approved accounts bind `from` (#3) here AND resolve the signing index in prepare().
    val input = WcSendAdapter.toSendInput(params, approvedAccounts.map { it.address }.toSet(), approvedChainIds)
    return prepare(input, approvedAccounts)
}
