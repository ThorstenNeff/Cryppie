package com.tneff.cyppie.feature.dca

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.aa.AaSigner
import com.tneff.cyppie.aa.DcaApi
import com.tneff.cyppie.aa.PendingDca
import com.tneff.cyppie.aa.SessionConfig
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.launch

/** MD-style state for the DCA overview (PRD-05 Ph1). [paused] = the backend kill-switch is on. */
sealed interface DcaUiState {
    data object Loading : DcaUiState
    data class Content(
        val sessions: List<SessionConfig>,
        val pending: List<PendingDca>,
        val paused: Boolean,
    ) : DcaUiState
    data object Error : DcaUiState
}

/**
 * KAN-138 (PRD-05 Ph1) — the DCA overview VM over the User-Service [DcaApi]: lists running sessions +
 * pending DCA ops + the kill-switch state; revokes a session; and drives the **on-device sign** of a
 * pending op (re-auth → fresh [SeedSource] → [AaSigner] delegate → submit). FR-4 graceful. The signing
 * mirrors Send/WC: a correct password yields a fresh seed source that [AaSigner] zeroizes right after.
 */
class DcaViewModel(
    private val api: DcaApi,
    private val signer: AaSigner,
    private val owner: EvmAddress,
    private val reauth: suspend (password: String) -> SeedSource?,
    // KAN-163 (b) NO-BLIND: recompute the buy userOpHash + bind digestToSign + scope-check the calls vs the
    // enabled session BEFORE signing (throws on mismatch → fail-closed). The app shell binds it to
    // `verifyDcaBuy(pending, owner, spendToken, router, swapSelector)`.
    private val verifyBuy: (PendingDca) -> Unit = {},
    // KAN-173: the active network's chain ids — sessions/pending ops on other chains are filtered out (the list
    // endpoints return all chains; client-side filter is the interim). Empty = no filter (back-compat).
    private val activeChainIds: Set<Long> = emptySet(),
) : ViewModel() {

    var uiState: DcaUiState by mutableStateOf(DcaUiState.Loading); private set
    /** Last sign error to surface (wrong password / submit failure); null when clear. */
    var signError: DcaError? by mutableStateOf(null); private set
    private var signingInFlight = false // P2: busy-guard against concurrent signs

    init { load() }

    fun retry() = load()

    private fun load() {
        uiState = DcaUiState.Loading
        viewModelScope.launch {
            uiState = runCatching {
                fun onActiveNet(chainId: Long) = activeChainIds.isEmpty() || chainId in activeChainIds
                DcaUiState.Content(
                    sessions = api.listSessions().filter { onActiveNet(it.chainId) },   // KAN-173: active-net only
                    pending = api.pendingDca().filter { onActiveNet(it.chainId) },        // KAN-173: active-net only
                    paused = runCatching { api.aaStatus().paused }.getOrDefault(true), // P1-4 fail-closed: unknown = paused
                )
            }.getOrElse { DcaUiState.Error }
        }
    }

    fun revoke(sessionId: String) {
        viewModelScope.launch {
            runCatching { api.revokeSession(sessionId) }
            load()
        }
    }

    /**
     * Re-auth + sign a pending DCA op on-device, then submit. A wrong password or a paused kill-switch
     * leaves the op unsigned and surfaces [signError]. The fresh seed source is zeroized by [AaSigner].
     */
    fun signPending(dca: PendingDca, password: String) {
        if (signingInFlight) return // P2 busy-guard
        signingInFlight = true
        signError = null
        viewModelScope.launch {
            // LOW-3: re-check the kill-switch VM-side, fail-closed, right before signing — it may have armed
            // between the last poll and now; an aaStatus failure is treated as paused (don't sign on unknown).
            val paused = runCatching { api.aaStatus().paused }.getOrDefault(true)
            if (paused) { load(); return@launch } // refresh → the screen shows the paused banner; nothing signed
            // Minimal seed window (HIGH-1 pattern): reauth → sign → zeroize, submit only after.
            // KAN-163 (b) NO-BLIND, fail-closed BEFORE any seed is in scope: recompute the userOpHash from the
            // built userOp → bind it to digestToSign → scope-check the calls (approve(spendToken→router)+swap on
            // the pinned router; C3 RAW-lock inside). A mismatch → never sign. (Subsumes the interim raw-lock.)
            if (runCatching { verifyBuy(dca) }.isFailure) { signError = DcaError.VERIFY_FAILED; signingInFlight = false; load(); return@launch }
            val outcome = runCatching {
                val source = reauth(password) ?: return@runCatching DcaError.WRONG_PASSWORD
                val signature = try {
                    signer.signDigest(dca.digestToSign, owner, source) // RAW sign of the userOpHash; zeroizes the source
                } finally {
                    (source as? AutoCloseable)?.close() // defensive zeroize even if signing throws
                }
                api.submitSignature(dca.id, signature) // → bundler userOpHash (poll receipt via opStatus)
                null // success
            }.getOrElse { DcaError.SUBMIT_FAILED }
            if (outcome != null) signError = outcome
            signingInFlight = false
            load()
        }
    }
}
