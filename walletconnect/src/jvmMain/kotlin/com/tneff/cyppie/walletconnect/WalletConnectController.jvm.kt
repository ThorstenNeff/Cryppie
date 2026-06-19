package com.tneff.cyppie.walletconnect

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Desktop (JVM) actual: WalletConnect is intentionally unavailable (ADR-0015) — no official SDK.
 * The UI gates WC actions off on Desktop; calls fail fast with [WalletConnectException.Unsupported].
 */
actual class WalletConnectController actual constructor() : WcTransport {
    actual override val events: Flow<WcEvent> = emptyFlow()
    actual override suspend fun pair(uri: String): Unit = unsupported()
    actual override suspend fun approveSession(proposalId: String, accounts: List<String>): Unit = unsupported()
    actual override suspend fun rejectSession(proposalId: String, reason: String): Unit = unsupported()
    // A read-only query, not an action — return "no approved chains" rather than throwing, so any shared
    // caller degrades gracefully on Desktop (where WC is gated off anyway). Empty = "any supported" fallback.
    actual override suspend fun approvedChains(topic: String): Set<Long> = emptySet()
    actual override suspend fun approvedAccounts(topic: String): Set<com.tneff.cyppie.evm.EvmAddress> = emptySet()

    actual override suspend fun respondRequest(requestId: Long, topic: String, result: String): Unit = unsupported()
    actual override suspend fun rejectRequest(requestId: Long, topic: String, reason: String): Unit = unsupported()
    actual override suspend fun disconnect(topic: String): Unit = unsupported()

    private fun unsupported(): Nothing = throw WalletConnectException.Unsupported()
}
