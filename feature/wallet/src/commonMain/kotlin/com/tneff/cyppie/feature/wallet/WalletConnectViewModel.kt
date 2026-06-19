package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.walletconnect.WalletConnectController
import com.tneff.cyppie.walletconnect.WcEvent
import com.tneff.cyppie.walletconnect.WcSessionProposal
import com.tneff.cyppie.walletconnect.WcSessionRequest
import com.tneff.cyppie.walletcore.EvmChain
import kotlinx.coroutines.launch

/**
 * WC-UI flow state (KAN-126 / SPEC_WC.md). The wallet is a bottom-sheet flow driven by
 * [WalletConnectController] events: [Idle] shows Pairing; an incoming proposal/request takes over with
 * full disclosure (no blind-signing). Request-Disclosure (Et.3) replaces the [Request] placeholder.
 */
sealed interface WcUiState {
    /** No active proposal/request — the Pairing entry is shown. */
    data object Idle : WcUiState

    /** A dApp session proposal awaiting approve/reject (Et.2). */
    data class Proposal(val proposal: WcSessionProposal) : WcUiState

    /** A signing request awaiting human-readable disclosure + approve/reject (Et.3). */
    data class Request(val request: WcSessionRequest) : WcUiState
}

/**
 * KAN-126 — drives the WalletConnect UI over [WalletConnectController]: pairs from a `wc:` URI, folds the
 * controller's event stream into [uiState], and approves/rejects session proposals (Et.2). The
 * Request-Disclosure sign path (WcSendAdapter→SendOrchestrator, incl. #4 chain-binding) arrives in Et.3.
 *
 * [error] is surfaced by **whatever screen is active** (the controller's OnError covers pairing,
 * approve/reject, respond, disconnect and relay — not only pairing — review L1), so a failure in any
 * state is visible, not just on the Pairing screen. [busy] gates the in-flight action's controls.
 */
class WalletConnectViewModel(
    private val controller: WalletConnectController,
    /** The wallet accounts offered to a dApp (CAIP-10 grant on approval). */
    private val accounts: List<EvmAccount>,
) : ViewModel() {

    var uiState: WcUiState by mutableStateOf(WcUiState.Idle); private set
    var busy: Boolean by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set

    /** Short addresses of the accounts that approval will share with the dApp (wc_accounts_label). */
    val sharedAccountLabels: List<String> = accounts.map { it.address.short() }

    init {
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is WcEvent.OnSessionProposal -> { uiState = WcUiState.Proposal(event.proposal); error = null }
                    is WcEvent.OnSessionRequest -> { uiState = WcUiState.Request(event.request); error = null }
                    is WcEvent.OnSessionSettled -> uiState = WcUiState.Idle
                    is WcEvent.OnSessionDeleted -> uiState = WcUiState.Idle
                    is WcEvent.OnError -> error = event.message // visible in the current screen, not just Pairing
                }
            }
        }
    }

    /** Pair with a dApp from a scanned/pasted `wc:` URI. Failure surfaces as [error] (no crash). */
    fun pair(uri: String) = runOp { controller.pair(uri.trim()) }

    /**
     * Approve [proposal], granting the wallet [accounts] on each requested **supported** chain as CAIP-10
     * (`eip155:<id>:0x…`). Unsupported requested chains are dropped (we only grant what we support).
     */
    fun approveSession(proposal: WcSessionProposal) = runOp {
        val grant = proposal.chains
            .mapNotNull { caip2 -> EvmChain.entries.firstOrNull { it.caip2.equals(caip2, ignoreCase = true) } }
            .flatMap { chain -> accounts.map { "${chain.caip2}:${it.address.value}" } }
        controller.approveSession(proposal.proposalId, grant)
        // OnSessionSettled returns us to Idle.
    }

    fun rejectSession(proposal: WcSessionProposal) = runOp {
        controller.rejectSession(proposal.proposalId, "User rejected")
        uiState = WcUiState.Idle // reject emits no settle event
    }

    fun dismissError() { error = null }

    /** Runs a controller op fail-closed: gate on [busy], clear [error], surface failures (no crash). */
    private fun runOp(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            runCatching { block() }.onFailure { error = it.message ?: "WalletConnect error" }
            busy = false
        }
    }
}
