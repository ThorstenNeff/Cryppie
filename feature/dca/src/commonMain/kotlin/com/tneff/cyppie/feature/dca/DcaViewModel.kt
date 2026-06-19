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
) : ViewModel() {

    var uiState: DcaUiState by mutableStateOf(DcaUiState.Loading); private set
    /** Last sign error to surface (wrong password / submit failure); null when clear. */
    var signError: DcaError? by mutableStateOf(null); private set

    init { load() }

    fun retry() = load()

    private fun load() {
        uiState = DcaUiState.Loading
        viewModelScope.launch {
            uiState = runCatching {
                DcaUiState.Content(
                    sessions = api.listSessions(),
                    pending = api.pendingDca(),
                    paused = runCatching { api.aaStatus().paused }.getOrDefault(false),
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
        signError = null
        viewModelScope.launch {
            // LOW-3: re-check the kill-switch VM-side, fail-closed, right before signing — it may have armed
            // between the last poll and now; an aaStatus failure is treated as paused (don't sign on unknown).
            val paused = runCatching { api.aaStatus().paused }.getOrDefault(true)
            if (paused) { load(); return@launch } // refresh → the screen shows the paused banner; nothing signed
            // Minimal seed window (HIGH-1 pattern): reauth → sign → zeroize, submit only after.
            val outcome = runCatching {
                val source = reauth(password) ?: return@runCatching DcaError.WRONG_PASSWORD
                val signature = try {
                    signer.signDigest(dca.userOpHash, owner, source) // zeroizes the source
                } finally {
                    (source as? AutoCloseable)?.close() // defensive zeroize even if signing throws
                }
                api.submitSignature(dca.id, signature)
                null // success
            }.getOrElse { DcaError.SUBMIT_FAILED }
            if (outcome != null) signError = outcome
            load()
        }
    }
}
