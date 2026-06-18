package com.tneff.cyppie.walletconnect

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android actual — **placeholder no-op** (Chunk 1 scaffold). Chunk 3 replaces this with the Reown
 * WalletKit integration (`com.reown:walletkit`, initialized in the app `Application`): pairing,
 * session proposals/requests delegated onto [events], approve/reject/respond via WalletKit.
 */
actual class WalletConnectController actual constructor() {
    actual val events: Flow<WcEvent> = emptyFlow()
    actual suspend fun pair(uri: String): Unit = unsupported()
    actual suspend fun approveSession(proposalId: String, accounts: List<String>): Unit = unsupported()
    actual suspend fun rejectSession(proposalId: String, reason: String): Unit = unsupported()
    actual suspend fun respondRequest(requestId: Long, topic: String, result: String): Unit = unsupported()
    actual suspend fun rejectRequest(requestId: Long, topic: String, reason: String): Unit = unsupported()
    actual suspend fun disconnect(topic: String): Unit = unsupported()

    private fun unsupported(): Nothing =
        throw WalletConnectException.Unsupported("Reown WalletKit integration lands in KAN-62 Chunk 3")
}
