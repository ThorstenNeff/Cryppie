package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletconnect.WalletConnectController
import com.tneff.cyppie.walletconnect.WalletConnectSigner
import com.tneff.cyppie.walletconnect.WcDecodedRequest
import com.tneff.cyppie.walletconnect.WcEvent
import com.tneff.cyppie.walletconnect.WcSessionProposal
import com.tneff.cyppie.walletconnect.WcSessionRequest
import com.tneff.cyppie.walletconnect.WcSigningRequest
import com.tneff.cyppie.walletconnect.decode
import com.tneff.cyppie.walletcore.EvmChain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WC-UI flow state (KAN-126 / SPEC_WC.md). [Idle] shows Pairing; an incoming proposal/request takes
 * over with full disclosure (no blind-signing). [Request] carries the raw request + its decoded form.
 */
sealed interface WcUiState {
    data object Idle : WcUiState
    data class Proposal(val proposal: WcSessionProposal) : WcUiState
    data class Request(val request: WcSessionRequest, val decoded: WcDecodedRequest) : WcUiState
}

/**
 * KAN-126 — drives the WalletConnect UI over [WalletConnectController]: pair, approve/reject sessions
 * (Et.2), and disclose+sign requests (Et.3a: personal_sign + EIP-712 sign-now). The sign path re-auths
 * (ADR-0009): the correct password / biometric decrypts a **fresh** [SeedSource], the request is signed
 * with exactly the disclosed payload (no blind-signing), and the source is closed/zeroized at once.
 * SendTx (eth_sendTransaction) prepare+broadcast with the #4 chain-binding lands in Et.3b.
 *
 * [error] is surfaced by whatever screen is active (controller OnError spans all ops — review L1).
 */
class WalletConnectViewModel(
    private val controller: WalletConnectController,
    private val accounts: List<EvmAccount>,
    /** Re-auth: decrypt a FRESH per-signature [SeedSource] from the password, or null on a wrong one. */
    private val reauth: suspend (CharArray) -> SeedSource?,
) : ViewModel() {

    var uiState: WcUiState by mutableStateOf(WcUiState.Idle); private set
    var busy: Boolean by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set

    /** Short addresses of the accounts that approval will share with the dApp (wc_accounts_label). */
    val sharedAccountLabels: List<String> = accounts.map { it.address.short() }

    // Re-auth (sign step, mirrors Send/KAN-110): a prompt between disclosure and signature. Fail-closed.
    var authorizing: Boolean by mutableStateOf(false); private set
    var authPassword: String by mutableStateOf(""); private set
    var authError: Boolean by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is WcEvent.OnSessionProposal -> { uiState = WcUiState.Proposal(event.proposal); error = null }
                    is WcEvent.OnSessionRequest -> onRequest(event.request)
                    is WcEvent.OnSessionSettled -> reset()
                    is WcEvent.OnSessionDeleted -> reset()
                    is WcEvent.OnError -> error = event.message
                }
            }
        }
    }

    fun pair(uri: String) = runOp { controller.pair(uri.trim()) }

    fun approveSession(proposal: WcSessionProposal) = runOp {
        val grant = proposal.chains
            .mapNotNull { caip2 -> EvmChain.entries.firstOrNull { it.caip2.equals(caip2, ignoreCase = true) } }
            .flatMap { chain -> accounts.map { "${chain.caip2}:${it.address.value}" } }
        controller.approveSession(proposal.proposalId, grant)
    }

    fun rejectSession(proposal: WcSessionProposal) = runOp {
        controller.rejectSession(proposal.proposalId, "User rejected")
        uiState = WcUiState.Idle
    }

    /** Decode the request (fail-closed → reject unsupported/malformed); else show its disclosure. */
    private fun onRequest(request: WcSessionRequest) {
        error = null
        val decoded = runCatching { request.decode() }.getOrNull()
        if (decoded == null) {
            viewModelScope.launch { runCatching { controller.rejectRequest(request.requestId, request.topic, "Unsupported request") } }
            uiState = WcUiState.Idle
            error = "Unsupported WalletConnect request"
            return
        }
        uiState = WcUiState.Request(request, decoded)
    }

    // ---- Sign step (Et.3a: personal_sign / EIP-712) ----

    fun startAuthorize() { if (currentSignRequest() != null) { authorizing = true; authError = false } }
    fun cancelAuthorize() { authorizing = false; authPassword = ""; authError = false }
    fun updateAuthPassword(value: String) { authPassword = value; authError = false }

    /** Password re-auth → fresh source → sign+respond (fail-closed: wrong password never signs). */
    fun authorizeAndSign() {
        if (busy || currentSignRequest() == null) return
        val pw = authPassword.toCharArray()
        busy = true
        authError = false
        viewModelScope.launch {
            val src = runCatching { reauth(pw) }.getOrNull()
            pw.fill(' ')
            if (src != null) {
                authPassword = ""
                signAndRespond(src)
            } else {
                authError = true
                busy = false
            }
        }
    }

    /** Biometric re-auth (KAN-119): a fresh source from the prompt → sign+respond. */
    fun submitBiometricSource(source: SeedSource) {
        if (busy || currentSignRequest() == null) return
        busy = true
        authPassword = ""
        viewModelScope.launch { signAndRespond(source) }
    }

    private fun currentSignRequest(): WcSigningRequest? =
        ((uiState as? WcUiState.Request)?.decoded as? WcDecodedRequest.SignNow)?.request

    /**
     * Signs exactly the decoded request over [source] off the main thread and responds to the dApp.
     * Account binding (#3) is enforced by [WalletConnectSigner.requireSignerMatches]. [source] is closed
     * (zeroized) whatever happens.
     */
    private suspend fun signAndRespond(source: SeedSource) {
        val state = uiState as? WcUiState.Request
        val req = (state?.decoded as? WcDecodedRequest.SignNow)?.request
        if (state == null || req == null) { (source as? AutoCloseable)?.close(); busy = false; return }
        val index = accounts.indexOfFirst { it.address == req.address }
        if (index < 0) {
            (source as? AutoCloseable)?.close(); error = "No matching account for this request"; authorizing = false; busy = false
            return
        }
        runCatching {
            val signature = withContext(Dispatchers.Default) {
                val signer = WalletConnectSigner(EvmKeyManager(source))
                when (req) {
                    is WcSigningRequest.PersonalSign -> signer.personalSign(req, index)
                    is WcSigningRequest.SignTypedDataV4 -> signer.signTypedDataV4(req, index)
                    is WcSigningRequest.SendTransaction -> error("SendTransaction is broadcast in Et.3b")
                }
            }
            controller.respondRequest(state.request.requestId, state.request.topic, "0x" + Hex.encode(signature))
        }.onFailure {
            runCatching { controller.rejectRequest(state.request.requestId, state.request.topic, "Signing failed") }
            error = it.message ?: "Signing failed"
        }
        (source as? AutoCloseable)?.close()
        authorizing = false
        busy = false
        if (error == null) uiState = WcUiState.Idle
    }

    fun dismissError() { error = null }

    private fun reset() { uiState = WcUiState.Idle; authorizing = false; authPassword = ""; authError = false }

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
