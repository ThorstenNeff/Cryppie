package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.send.PreparedSend
import com.tneff.cyppie.send.SendOrchestrator
import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletconnect.WalletConnectSigner
import com.tneff.cyppie.walletconnect.WcTransport
import com.tneff.cyppie.walletconnect.WcDecodedRequest
import com.tneff.cyppie.walletconnect.WcEvent
import com.tneff.cyppie.walletconnect.WcSessionProposal
import com.tneff.cyppie.walletconnect.WcSessionRequest
import com.tneff.cyppie.walletconnect.WcSigningRequest
import com.tneff.cyppie.walletconnect.decode
import com.tneff.cyppie.walletconnect.prepareWalletConnectSend
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
    private val controller: WcTransport,
    private val accounts: List<EvmAccount>,
    /** Re-auth: decrypt a FRESH per-signature [SeedSource] from the password, or null on a wrong one. */
    private val reauth: suspend (CharArray) -> SeedSource?,
    /** Send pipeline for eth_sendTransaction (Et.3b): prepare (complete+bind) → sign → broadcast. */
    private val sendOrchestrator: SendOrchestrator,
) : ViewModel() {

    /** The completed+bound SendTx awaiting approval (Et.3b); null until prepared / for non-SendTx. */
    var preparedSend: PreparedSend? by mutableStateOf(null); private set
    var preparing: Boolean by mutableStateOf(false); private set

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
        preparedSend = null
        uiState = WcUiState.Request(request, decoded)
        // Et.3b: eth_sendTransaction must be completed (nonce/fees) + bound BEFORE disclosure (no TOCTOU).
        // Bind to the SESSION-approved accounts (#3) + chains (#4) — both read from the SDK session store.
        if (decoded is WcDecodedRequest.SendTransaction) {
            preparing = true
            viewModelScope.launch {
                preparedSend = runCatching {
                    val approvedChainIds = controller.approvedChains(request.topic)
                    val approvedAccts = accounts.filter { it.address in controller.approvedAccounts(request.topic) }
                    sendOrchestrator.prepareWalletConnectSend(decoded.params, approvedAccts, approvedChainIds)
                }.onFailure { error = it.message ?: "Could not prepare the transaction" }.getOrNull()
                preparing = false
            }
        }
    }

    // ---- Sign/send step (Et.3a sign-now + Et.3b SendTx) ----

    /** Whether the request is ready to authorize: a sign-now request, or a prepared SendTx. */
    private fun canAuthorize(): Boolean = currentSignRequest() != null || preparedSend != null

    fun startAuthorize() { if (canAuthorize()) { authorizing = true; authError = false } }
    fun cancelAuthorize() { authorizing = false; authPassword = ""; authError = false }
    fun updateAuthPassword(value: String) { authPassword = value; authError = false }

    /** Password re-auth → fresh source → sign/send+respond (fail-closed: wrong password never signs). */
    fun authorizeAndSign() {
        if (busy || !canAuthorize()) return
        val pw = authPassword.toCharArray()
        busy = true
        authError = false
        viewModelScope.launch {
            val src = runCatching { reauth(pw) }.getOrNull()
            pw.fill(' ')
            if (src != null) {
                authPassword = ""
                submitSource(src)
            } else {
                authError = true
                busy = false
            }
        }
    }

    /** Biometric re-auth (KAN-119): a fresh source from the prompt → sign/send+respond. */
    fun submitBiometricSource(source: SeedSource) {
        if (busy || !canAuthorize()) return
        busy = true
        authPassword = ""
        viewModelScope.launch { submitSource(source) }
    }

    /** Dispatch the re-authed source: broadcast a prepared SendTx (Et.3b) or sign-now (Et.3a). */
    private suspend fun submitSource(source: SeedSource) {
        if (preparedSend != null) signAndBroadcastSend(source) else signAndRespond(source)
    }

    /**
     * Et.3b — sign the prepared SendTx over [source] (closed/zeroized by signAndBroadcast, M1), broadcast
     * it, and respond to the dApp with the tx hash. Signs exactly the disclosed [preparedSend] (no TOCTOU).
     */
    private suspend fun signAndBroadcastSend(source: SeedSource) {
        val state = uiState as? WcUiState.Request
        val prepared = preparedSend
        if (state == null || prepared == null) { (source as? AutoCloseable)?.close(); busy = false; return }
        runCatching {
            val result = sendOrchestrator.signAndBroadcast(prepared, source)
            controller.respondRequest(state.request.requestId, state.request.topic, result.txHash)
        }.onFailure {
            runCatching { controller.rejectRequest(state.request.requestId, state.request.topic, "Transaction failed") }
            error = it.message ?: "Transaction failed"
        }
        authorizing = false
        busy = false
        if (error == null) reset()
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
        // M3 (review): the signer must be an account this session actually approved — not merely a known
        // wallet account (parallel to the #4 chain-binding). Fail-closed: an unknown/unapproved session
        // (empty set) rejects. Read from the SDK session store via the transport (survives restart).
        val approved = runCatching { controller.approvedAccounts(state.request.topic) }.getOrElse { emptySet() }
        if (req.address !in approved) {
            (source as? AutoCloseable)?.close()
            runCatching { controller.rejectRequest(state.request.requestId, state.request.topic, "Account not approved for this session") }
            error = "Signing account is not approved for this session"; authorizing = false; busy = false
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

    private fun reset() {
        uiState = WcUiState.Idle; authorizing = false; authPassword = ""; authError = false
        preparedSend = null; preparing = false
    }

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
