package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import kotlinx.coroutines.flow.Flow

/**
 * The WalletConnect transport seam (ADR-0015). The app / send-VM depends on **this interface**, not the concrete
 * [WalletConnectController], so a deterministic test double can be injected for UI-E2E (Maestro) and integration
 * tests — no live relay, no real dApp, reproducible.
 *
 * The production binding is [WalletConnectController] (the `expect`/`actual` over Reown WalletKit / reown-swift /
 * Desktop no-op). The E2E test double ships in a **debug-only** source set (KAN-127) so it can never reach a
 * release build — DI binds it only in debug.
 *
 * 🔒 Key-path invariant (unchanged, ADR-0005): transport only — the seed/private key never crosses this boundary;
 * only the **signature result string** flows back via [respondRequest]. Signing stays in `:wallet`.
 */
interface WcTransport {

    /** Cold stream of session/request lifecycle events. */
    val events: Flow<WcEvent>

    /** Pair with a dapp from a `wc:` URI (scanned QR / pasted). */
    suspend fun pair(uri: String)

    /** Approve a session proposal, granting [accounts] (CAIP-10, e.g. "eip155:1:0xabc…"). */
    suspend fun approveSession(proposalId: String, accounts: List<String>)

    /**
     * The EIP-155 chain ids the session [topic] approved, read from the SDK session store (robust across
     * persisted/restored sessions). Empty when the session is unknown/expired or has no EVM chains. Feeds
     * the #4 chain-binding check in [prepareWalletConnectSend].
     */
    suspend fun approvedChains(topic: String): Set<Long>

    /**
     * The EVM addresses the session [topic] approved (CAIP-10 accounts → [EvmAddress]), read from the SDK
     * session store. Empty when the session is unknown/expired. Backs the WC-sign **account binding** (#3 /
     * M3): a request may sign only with `address ∈ approvedAccounts(topic)`, not merely any known wallet
     * account the dApp names.
     */
    suspend fun approvedAccounts(topic: String): Set<EvmAddress>

    /** Reject a session proposal. */
    suspend fun rejectSession(proposalId: String, reason: String)

    /** Respond to a request with its [result] (signature hex / tx hash). */
    suspend fun respondRequest(requestId: Long, topic: String, result: String)

    /** Reject a request (user declined / validation failed). */
    suspend fun rejectRequest(requestId: Long, topic: String, reason: String)

    /** Disconnect a session. */
    suspend fun disconnect(topic: String)
}
