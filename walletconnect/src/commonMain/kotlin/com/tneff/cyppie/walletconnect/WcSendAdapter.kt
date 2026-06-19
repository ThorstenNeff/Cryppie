package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
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
        val id = caip2ChainIdOrNull(caip2)
        return id?.let { EvmChain.fromChainId(it) }
            ?: throw WalletConnectException.UnsupportedRequest("Unsupported WalletConnect chain: $caip2")
    }
}

/**
 * Parses an EIP-155 chain id from a CAIP-2 ref (`eip155:1`), a bare numeric id (`1`), or an `eip155:`-prefixed
 * id. Returns `null` for any non-`eip155` namespace (e.g. `cosmos:…`) or an unparseable reference — so callers
 * keep only the EVM chains they actually support. Shared by [WcSendAdapter] (#4 resolution) and the
 * [WalletConnectController.approvedChains] actuals.
 */
fun caip2ChainIdOrNull(caip2: String): Long? {
    if (':' !in caip2) return caip2.toLongOrNull()
    val (namespace, reference) = caip2.split(':', limit = 2)
    return if (namespace.equals("eip155", ignoreCase = true)) reference.toLongOrNull() else null
}

/**
 * Derives the approved EIP-155 chain ids of a WalletConnect session from its namespace [chains] (CAIP-2,
 * e.g. `eip155:1`) and [accounts] (CAIP-10, e.g. `eip155:1:0xabc…`). An account always carries its chain ref
 * (its CAIP-2 prefix), so accounts are a robust fallback when a namespace omits an explicit `chains` list —
 * which keeps [WalletConnectController.approvedChains] correct across persisted/restored sessions. Non-EVM
 * refs are dropped. The platform `actual`s fetch the raw lists from the SDK session store and call this; the
 * result feeds [WcSendAdapter.toSendInput]'s `approvedChainIds` (#4 chain-binding, replay defence).
 */
fun approvedChainIdsFrom(chains: List<String>, accounts: List<String>): Set<Long> =
    (chains + accounts.map { it.substringBeforeLast(':') })
        .mapNotNull { caip2ChainIdOrNull(it) }
        .toSet()

/**
 * Extracts the EVM address from a CAIP-10 account ref (`eip155:1:0xabc…`). Returns `null` for a non-`eip155`
 * namespace, a malformed ref, or an unparseable address — so non-EVM accounts are dropped. Mirrors
 * [caip2ChainIdOrNull] for the address half of the session store.
 */
fun caip10AddressOrNull(caip10: String): EvmAddress? {
    val parts = caip10.split(':')
    if (parts.size != 3 || !parts[0].equals("eip155", ignoreCase = true)) return null
    val bytes = Hex.decodeOrNull(parts[2]) ?: return null
    return runCatching { EvmAddress.fromBytes(bytes) }.getOrNull()
}

/**
 * The EVM addresses a session approved, from its namespace [accounts] (CAIP-10). Same single-source pattern as
 * [approvedChainIdsFrom]: the platform `actual`s fetch the raw account list from the SDK session store and call
 * this. Feeds the WC-sign **account binding** (#3 / M3) — `req.address ∈ approvedAccounts(topic)`, so a request
 * may only sign with an address the session actually authorized, not merely any known wallet account.
 */
fun approvedAddressesFrom(accounts: List<String>): Set<EvmAddress> =
    accounts.mapNotNull { caip10AddressOrNull(it) }.toSet()

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
