package com.tneff.cyppie.feature.copy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.aa.CopySession
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.launch

/** Load state of the Active-copies overview (`Copy0-Active`). Error is retryable; Empty is never silent. */
enum class CopyListState { Loading, Loaded, Empty, Error }

/** Why a revoke attempt didn't complete — drives a precise message; either way the session stays active. */
enum class RevokeError { WrongPassword, Failed }

/**
 * KAN-157 — the Copy-session-management VM (Active list + on-chain Revoke). Pure **functional lambda-seams**
 * (like [FollowViewModel]) so it stays unit-testable + decoupled from `:aa`: [listSessions] fetches the
 * granted sessions, [revokeSession] runs Dev-2's no-blind `RevokeBroadcaster` (build → `verifyRevokeUserOp`
 * → owner-sign in the broadcaster's own seed window → submit → poll), [reauth] mints a fresh per-op
 * [SeedSource] (ADR-0009). The app shell binds them to `KtorCopyApi` + `RevokeBroadcaster`.
 *
 * **fail-safe (the spec's hard rule):** a row is removed ONLY after a confirmed on-chain revoke receipt; any
 * failure — wrong password, verify-throw, revert, timeout — keeps the session shown as **still active**. We
 * never falsely suggest a copy has "ended".
 */
class CopySessionsViewModel(
    private val listSessions: suspend () -> List<CopySession>,
    private val revokeSession: suspend (session: CopySession, seed: SeedSource) -> Unit,
    private val reauth: suspend (password: String) -> SeedSource?,
) : ViewModel() {

    var listState: CopyListState by mutableStateOf(CopyListState.Loading); private set
    var sessions: List<CopySession> by mutableStateOf(emptyList()); private set

    // Revoke-Confirm dialog state (null target = no dialog).
    var revokeTarget: CopySession? by mutableStateOf(null); private set
    var revoking: Boolean by mutableStateOf(false); private set
    var revokeError: RevokeError? by mutableStateOf(null); private set
    var lastRevoked: Boolean by mutableStateOf(false); private set // one-shot: drives the copy_revoked confirmation

    /** (Re)load the active list. Empty → [CopyListState.Empty]; a failure → [CopyListState.Error] (never a silent empty). */
    fun load() {
        listState = CopyListState.Loading
        viewModelScope.launch {
            runCatching { listSessions() }
                .onSuccess { sessions = it; listState = if (it.isEmpty()) CopyListState.Empty else CopyListState.Loaded }
                .onFailure { listState = CopyListState.Error }
        }
    }

    fun askRevoke(session: CopySession) { revokeTarget = session; revokeError = null; lastRevoked = false }
    fun dismissRevoke() { if (!revoking) { revokeTarget = null; revokeError = null } }

    /**
     * Re-auth → no-blind on-chain `removeSession` (the [revokeSession] seam owns the seed-zeroize, so we do
     * NOT close it here). On a confirmed receipt the row is dropped optimistically (we don't auto-reload, so a
     * just-revoked row can't flicker back while the backend's off-chain mark settles). Any failure keeps the
     * session active (fail-safe) and surfaces a retryable [RevokeError].
     */
    fun confirmRevoke(password: String) {
        val session = revokeTarget ?: return
        if (revoking) return
        revoking = true; revokeError = null
        viewModelScope.launch {
            val result: RevokeError? = runCatching {
                val source = reauth(password) ?: return@runCatching RevokeError.WrongPassword
                revokeSession(session, source) // broadcaster owns the use{}-zeroize; exactly one close
                null // success
            }.getOrElse { RevokeError.Failed }
            revoking = false
            if (result == null) {
                sessions = sessions.filterNot { it.permissionId == session.permissionId }
                revokeTarget = null
                lastRevoked = true
                listState = if (sessions.isEmpty()) CopyListState.Empty else CopyListState.Loaded
            } else {
                revokeError = result // dialog stays open, session stays listed = still active
            }
        }
    }

    /** Consume the one-shot copy_revoked confirmation after it's been shown. */
    fun consumeRevokedMessage() { lastRevoked = false }
}
