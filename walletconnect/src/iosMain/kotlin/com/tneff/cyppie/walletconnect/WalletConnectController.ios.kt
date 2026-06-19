package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The Swift-side contract (ADR-0015): reown-swift is a Swift Package and isn't consumable from
 * Kotlin/Native, so the iOS app implements this and forwards calls to reown-swift. The Kotlin
 * `actual` delegates to it. The real Swift shim lives in the **iOS app (Xcode, out-of-repo)** and is
 * device-tested via KAN-63 — documented follow-up.
 */
interface WalletConnectBridge {
    fun pair(uri: String)
    fun approveSession(proposalId: String, accounts: List<String>)
    fun rejectSession(proposalId: String, reason: String)
    fun respondRequest(requestId: Long, topic: String, result: String)
    fun rejectRequest(requestId: Long, topic: String, reason: String)
    fun disconnect(topic: String)

    /**
     * The approved chain refs for session [topic], read from reown-swift's session store (e.g.
     * `session.namespaces.values` → `chains`, falling back to each account's CAIP-2 prefix). Return
     * CAIP-2 (`eip155:1`) and/or CAIP-10 (`eip155:1:0x…`) strings — the Kotlin side parses & filters to
     * EVM chain ids. Empty for an unknown session. Keeps chain-id parsing single-sourced in Kotlin.
     */
    fun approvedChains(topic: String): List<String>

    /**
     * The approved **CAIP-10 accounts** for session [topic] (e.g. `eip155:1:0x…`), from reown-swift's session
     * store. The Kotlin side extracts the [EvmAddress] (non-EVM dropped) — address parsing single-sourced in
     * Kotlin. Empty for an unknown session.
     */
    fun approvedAccounts(topic: String): List<String>
}

/**
 * Registry wiring the iOS app's Swift shim to the Kotlin controller. The app sets [bridge] during WC
 * init and pushes reown-swift delegate events through the primitive-friendly `emit*` helpers (Swift
 * can't easily construct the sealed [WcEvent] types).
 *
 * 🔒 Key-path invariant: only transport data + the **signature result string** cross this boundary —
 * never the seed/private key (signing stays in `:wallet`).
 */
object WalletConnectIos {
    var bridge: WalletConnectBridge? = null

    internal val events = MutableSharedFlow<WcEvent>(extraBufferCapacity = 64)

    fun emitSessionProposal(
        proposalId: String,
        dappName: String,
        dappDescription: String,
        dappUrl: String,
        chains: List<String>,
        methods: List<String>,
        verifyContext: String?,
    ) {
        events.tryEmit(
            WcEvent.OnSessionProposal(
                WcSessionProposal(
                    proposalId = proposalId,
                    dapp = WcDappMetadata(dappName, dappDescription, dappUrl, verify = WcVerify.from(verifyContext)),
                    chains = chains,
                    methods = methods,
                ),
            ),
        )
    }

    fun emitSessionRequest(
        requestId: Long,
        topic: String,
        chainId: String,
        method: String,
        params: String,
        dappName: String,
        dappUrl: String,
        verifyContext: String?,
    ) {
        events.tryEmit(
            WcEvent.OnSessionRequest(
                WcSessionRequest(
                    requestId = requestId,
                    topic = topic,
                    chainId = chainId,
                    method = method,
                    params = params,
                    dapp = WcDappMetadata(dappName, "", dappUrl, verify = WcVerify.from(verifyContext)),
                ),
            ),
        )
    }

    fun emitSessionSettled(topic: String) { events.tryEmit(WcEvent.OnSessionSettled(topic)) }
    fun emitSessionDeleted(topic: String) { events.tryEmit(WcEvent.OnSessionDeleted(topic)) }
    fun emitError(message: String) { events.tryEmit(WcEvent.OnError(message)) }
}

/**
 * iOS `actual` — delegates to the app-provided [WalletConnectBridge] over reown-swift. Fails fast
 * with [WalletConnectException.Unsupported] if the bridge isn't wired (e.g. on a Desktop-like build).
 */
actual class WalletConnectController actual constructor() : WcTransport {
    actual override val events: Flow<WcEvent> = WalletConnectIos.events.asSharedFlow()

    actual override suspend fun pair(uri: String) = bridge().pair(uri)
    actual override suspend fun approveSession(proposalId: String, accounts: List<String>) = bridge().approveSession(proposalId, accounts)
    actual override suspend fun rejectSession(proposalId: String, reason: String) = bridge().rejectSession(proposalId, reason)
    // Parse the shim's refs robustly whether they arrive as CAIP-2 or CAIP-10 (passing the list as both
    // `chains` and `accounts` lets approvedChainIdsFrom normalize each form; the Set dedups).
    actual override suspend fun approvedChains(topic: String): Set<Long> =
        bridge().approvedChains(topic).let { refs -> approvedChainIdsFrom(refs, refs) }

    actual override suspend fun approvedAccounts(topic: String): Set<EvmAddress> =
        approvedAddressesFrom(bridge().approvedAccounts(topic))

    actual override suspend fun respondRequest(requestId: Long, topic: String, result: String) = bridge().respondRequest(requestId, topic, result)
    actual override suspend fun rejectRequest(requestId: Long, topic: String, reason: String) = bridge().rejectRequest(requestId, topic, reason)
    actual override suspend fun disconnect(topic: String) = bridge().disconnect(topic)

    private fun bridge(): WalletConnectBridge =
        WalletConnectIos.bridge ?: throw WalletConnectException.Unsupported("iOS WalletConnect bridge not initialized")
}
