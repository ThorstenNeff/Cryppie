package com.tneff.cyppie.feature.strat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.launch

/** Load state of the `Strat2-List` overview. Error is retryable; Empty is never silent. */
enum class StratListState { Loading, Loaded, Empty, Error }

/** Why a revoke didn't complete — drives a precise message; either way the strategy stays active (fail-safe). */
enum class StratRevokeError { WrongPassword, Failed }

/**
 * KAN-166 — the Strategy management VM (`Strat2-List` + on-chain Revoke). Mirrors Copy's CopySessionsViewModel:
 * functional lambda-seams, and the **fail-safe** revoke rule — a row is removed ONLY after a confirmed
 * on-chain revoke receipt; any failure keeps the strategy shown as still active (never falsely "ended").
 */
class StrategyListViewModel(
    private val listStrategies: suspend () -> List<StrategySession>,
    private val revokeStrategy: suspend (session: StrategySession, seed: SeedSource) -> Unit,
    private val reauth: suspend (password: String) -> SeedSource?,
    private val setPaused: suspend (session: StrategySession, paused: Boolean) -> Unit = { _, _ -> },
) : ViewModel() {

    var listState: StratListState by mutableStateOf(StratListState.Loading); private set
    var sessions: List<StrategySession> by mutableStateOf(emptyList()); private set

    // Strat3-Detail: the tapped strategy (null = the list). Pause/Resume is the instant off-chain kill-switch.
    var detailTarget: StrategySession? by mutableStateOf(null); private set
    var togglingPause: Boolean by mutableStateOf(false); private set

    var revokeTarget: StrategySession? by mutableStateOf(null); private set
    var revoking: Boolean by mutableStateOf(false); private set
    var revokeError: StratRevokeError? by mutableStateOf(null); private set
    var lastRevoked: Boolean by mutableStateOf(false); private set

    fun load() {
        listState = StratListState.Loading
        viewModelScope.launch {
            runCatching { listStrategies() }
                .onSuccess { sessions = it; listState = if (it.isEmpty()) StratListState.Empty else StratListState.Loaded }
                .onFailure { listState = StratListState.Error }
        }
    }

    fun openDetail(session: StrategySession) { detailTarget = session }
    fun closeDetail() { if (!togglingPause) detailTarget = null }

    /** Pause/Resume the detail strategy — instant off-chain kill-switch (no sign). On success the local status
     *  flips in both [detailTarget] and the [sessions] list; on failure the prior state is kept (fail-safe). */
    fun togglePause() {
        val session = detailTarget ?: return
        if (togglingPause) return
        val nextPaused = session.status != "paused" // active → pause; paused → resume
        togglingPause = true
        viewModelScope.launch {
            val ok = runCatching { setPaused(session, nextPaused); true }.getOrElse { false }
            togglingPause = false
            if (ok) {
                val updated = session.copy(status = if (nextPaused) "paused" else "active")
                sessions = sessions.map { if (it.sessionId == session.sessionId) updated else it }
                detailTarget = updated
            }
        }
    }

    fun askRevoke(session: StrategySession) { revokeTarget = session; revokeError = null; lastRevoked = false }
    fun dismissRevoke() { if (!revoking) { revokeTarget = null; revokeError = null } }

    fun confirmRevoke(password: String) {
        val session = revokeTarget ?: return
        if (revoking) return
        revoking = true; revokeError = null
        viewModelScope.launch {
            val result: StratRevokeError? = runCatching {
                val source = reauth(password) ?: return@runCatching StratRevokeError.WrongPassword
                revokeStrategy(session, source) // seam owns the seed-zeroize
                null
            }.getOrElse { StratRevokeError.Failed }
            revoking = false
            if (result == null) {
                sessions = sessions.filterNot { it.sessionId == session.sessionId }
                revokeTarget = null
                if (detailTarget?.sessionId == session.sessionId) detailTarget = null // revoked from Detail → back to list
                lastRevoked = true
                listState = if (sessions.isEmpty()) StratListState.Empty else StratListState.Loaded
            } else {
                revokeError = result // dialog stays open, strategy stays listed = still active
            }
        }
    }

    fun consumeRevokedMessage() { lastRevoked = false }
}
