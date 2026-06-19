package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.walletconnect.WalletConnectController
import com.tneff.cyppie.walletconnect.WcEvent
import com.tneff.cyppie.walletconnect.WcSessionProposal
import com.tneff.cyppie.walletconnect.WcSessionRequest
import kotlinx.coroutines.launch

/**
 * WC-UI flow state (KAN-126 / SPEC_WC.md). The wallet is a bottom-sheet flow driven by
 * [WalletConnectController] events: [Idle] shows Pairing; an incoming proposal/request takes over with
 * full disclosure (no blind-signing). Approve/reject screens land in Et.2 (Session-Approval) / Et.3
 * (Request-Disclosure).
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
 * KAN-126 (Et.1) — drives the WalletConnect UI over [WalletConnectController]: pairs from a `wc:` URI
 * and folds the controller's event stream into [uiState]. Approve/reject of sessions and requests
 * (and the WcSendAdapter→SendOrchestrator sign path with explicit #4 chain-binding) arrive in Et.2/Et.3.
 */
class WalletConnectViewModel(
    private val controller: WalletConnectController,
) : ViewModel() {

    var uiState: WcUiState by mutableStateOf(WcUiState.Idle); private set
    var pairing: Boolean by mutableStateOf(false); private set
    var pairError: String? by mutableStateOf(null); private set

    init {
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is WcEvent.OnSessionProposal -> uiState = WcUiState.Proposal(event.proposal)
                    is WcEvent.OnSessionRequest -> uiState = WcUiState.Request(event.request)
                    is WcEvent.OnSessionSettled -> uiState = WcUiState.Idle
                    is WcEvent.OnSessionDeleted -> uiState = WcUiState.Idle
                    is WcEvent.OnError -> pairError = event.message
                }
            }
        }
    }

    /** Pair with a dApp from a scanned/pasted `wc:` URI. Failure surfaces as [pairError] (no crash). */
    fun pair(uri: String) {
        if (pairing) return
        pairing = true
        pairError = null
        viewModelScope.launch {
            runCatching { controller.pair(uri.trim()) }
                .onFailure { pairError = it.message ?: "Pairing failed" }
            pairing = false
        }
    }

    fun dismissError() { pairError = null }
}
