package com.tneff.cyppie.walletconnect

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS actual — **placeholder no-op** (Chunk 1 scaffold). Chunk 4 replaces this with a Swift shim over
 * reown-swift (initialized in the iOS app): the shim forwards proposals/requests onto [events] and
 * implements approve/reject/respond, called from Kotlin/Native.
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
        throw WalletConnectException.Unsupported("reown-swift shim lands in KAN-62 Chunk 4")
}
