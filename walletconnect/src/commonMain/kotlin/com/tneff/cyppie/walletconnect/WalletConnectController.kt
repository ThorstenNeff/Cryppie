package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
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
 *
 * ## 🔒 Key-path invariant (ADR-0005 / ADR-0018 security gate)
 * The WalletConnect SDK is **transport only** and must **never** receive the seed or a private key.
 * Signing happens exclusively in `:wallet` ([WalletConnectSigner] over `EvmKeyManager`); only the
 * **signature result** (a hex string) flows back here via [respondRequest]. Structurally enforced:
 * the Reown / `kethereum` types live solely in this module's platform sources (`androidMain` + the
 * iOS Swift shim) — never in `:wallet`/`:evm` — so no WC/SDK type touches the derivation/signing path.
 */
expect class WalletConnectController() : WcTransport {

    /** Cold stream of session/request lifecycle events. */
    override val events: Flow<WcEvent>

    /** Pair with a dapp from a `wc:` URI (scanned QR / pasted). */
    override suspend fun pair(uri: String)

    /** Approve a session proposal, granting [accounts] (CAIP-10, e.g. "eip155:1:0xabc…"). */
    override suspend fun approveSession(proposalId: String, accounts: List<String>)

    /** Reject a session proposal. */
    override suspend fun rejectSession(proposalId: String, reason: String)

    /**
     * The EIP-155 chain ids the session [topic] approved, read from the SDK session store — so it stays
     * correct across **persisted/restored sessions** (app restart), not just freshly-settled ones. Returns
     * an empty set when the session is unknown/expired or has no EVM chains.
     *
     * Closes the **#4 chain-binding** gap: a [WcSessionRequest] carries only its own `chainId`, so without
     * this the send VM would have to fall back to "any supported chain" — a replay surface. The VM passes
     * the result into [prepareWalletConnectSend]'s `approvedChainIds`, which rejects a request whose chain
     * the session never approved.
     */
    override suspend fun approvedChains(topic: String): Set<Long>

    /**
     * The EVM addresses the session [topic] approved (CAIP-10 accounts → [EvmAddress]) from the SDK session
     * store; empty when unknown/expired. Backs WC-sign account binding (#3 / M3) — `address ∈ approvedAccounts`.
     */
    override suspend fun approvedAccounts(topic: String): Set<EvmAddress>

    /** Respond to a request with its [result] (signature hex / tx hash). */
    override suspend fun respondRequest(requestId: Long, topic: String, result: String)

    /** Reject a request (user declined / validation failed). */
    override suspend fun rejectRequest(requestId: Long, topic: String, reason: String)

    /** Disconnect a session. */
    override suspend fun disconnect(topic: String)
}
