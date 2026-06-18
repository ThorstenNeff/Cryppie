package com.tneff.cyppie.walletconnect

import kotlinx.coroutines.flow.Flow

/**
 * Platform WalletConnect v2 transport (ADR-0015), behind a common API. Events reach `commonMain` as
 * [events]; the wallet approves/rejects and fulfils requests. `expect`/`actual` because WC has no
 * KMP SDK: Android = Reown WalletKit (Chunk 3), iOS = reown-swift via a Swift shim (Chunk 4),
 * Desktop = no-op. Signing itself is done by [WalletConnectSigner] (common, over `:wallet`).
 *
 * The Reown/Swift singletons are initialized by the app layer (Android `Application`; iOS Swift); a
 * no-arg `actual` constructor binds to those — keeping platform config (project-id/context) out of
 * `commonMain`.
 */
expect class WalletConnectController() {

    /** Cold stream of session/request lifecycle events. */
    val events: Flow<WcEvent>

    /** Pair with a dapp from a `wc:` URI (scanned QR / pasted). */
    suspend fun pair(uri: String)

    /** Approve a session proposal, granting [accounts] (CAIP-10, e.g. "eip155:1:0xabc…"). */
    suspend fun approveSession(proposalId: String, accounts: List<String>)

    /** Reject a session proposal. */
    suspend fun rejectSession(proposalId: String, reason: String)

    /** Respond to a request with its [result] (signature hex / tx hash). */
    suspend fun respondRequest(requestId: Long, topic: String, result: String)

    /** Reject a request (user declined / validation failed). */
    suspend fun rejectRequest(requestId: Long, topic: String, reason: String)

    /** Disconnect a session. */
    suspend fun disconnect(topic: String)
}
