package com.tneff.cyppie.walletconnect

import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android actual backed by Reown WalletKit (ADR-0015). [WalletConnectInitializer.initialize] must run
 * first (in the app `Application`). Maps WalletKit delegate callbacks onto [events] and forwards
 * approve/reject/respond to WalletKit. Compile-verified; runtime behaviour is covered by KAN-63 +
 * the app integration (needs a project-id, relay and device).
 *
 * Signing is **not** done here — the app decodes the request ([WcSessionRequest.decode]), completes &
 * discloses it, signs via [WalletConnectSigner], then calls [respondRequest].
 */
actual class WalletConnectController actual constructor() {

    private val _events = MutableSharedFlow<WcEvent>(extraBufferCapacity = 64)
    actual val events: Flow<WcEvent> = _events.asSharedFlow()

    // Cache proposals by proposerPublicKey so approveSession can run generateApprovedNamespaces.
    private val proposals = HashMap<String, Wallet.Model.SessionProposal>()

    init {
        WalletKit.setWalletDelegate(object : WalletKit.WalletDelegate {
            override fun onSessionProposal(sessionProposal: Wallet.Model.SessionProposal, verifyContext: Wallet.Model.VerifyContext) {
                proposals[sessionProposal.proposerPublicKey] = sessionProposal
                _events.tryEmit(WcEvent.OnSessionProposal(sessionProposal.toWcProposal(verifyContext)))
            }

            override fun onSessionRequest(sessionRequest: Wallet.Model.SessionRequest, verifyContext: Wallet.Model.VerifyContext) {
                _events.tryEmit(WcEvent.OnSessionRequest(sessionRequest.toWcRequest(verifyContext)))
            }

            override fun onSessionDelete(sessionDelete: Wallet.Model.SessionDelete) {
                val topic = (sessionDelete as? Wallet.Model.SessionDelete.Success)?.topic
                _events.tryEmit(WcEvent.OnSessionDeleted(topic ?: ""))
            }

            override fun onSessionSettleResponse(settleSessionResponse: Wallet.Model.SettledSessionResponse) {
                if (settleSessionResponse is Wallet.Model.SettledSessionResponse.Result) {
                    _events.tryEmit(WcEvent.OnSessionSettled(settleSessionResponse.session.topic))
                }
            }

            override fun onSessionUpdateResponse(sessionUpdateResponse: Wallet.Model.SessionUpdateResponse) = Unit
            override fun onConnectionStateChange(state: Wallet.Model.ConnectionState) = Unit
            override fun onError(error: Wallet.Model.Error) {
                _events.tryEmit(WcEvent.OnError(error.throwable.message ?: "WalletConnect error"))
            }

            override fun onProposalExpired(proposal: Wallet.Model.ExpiredProposal) {
                proposals.remove(proposal.pairingTopic)
            }
            override fun onRequestExpired(request: Wallet.Model.ExpiredRequest) = Unit
            override fun onSessionExtend(session: Wallet.Model.Session) = Unit
        })
    }

    actual suspend fun pair(uri: String) {
        WalletKit.pair(Wallet.Params.Pair(uri)) { error ->
            _events.tryEmit(WcEvent.OnError(error.throwable.message ?: "Pairing failed"))
        }
    }

    actual suspend fun approveSession(proposalId: String, accounts: List<String>) {
        val proposal = proposals[proposalId]
            ?: throw WalletConnectException.Pairing("Unknown session proposal $proposalId")
        val supported = Wallet.Model.Namespace.Session(
            chains = accounts.map { it.substringBeforeLast(':') }.distinct(),
            methods = SUPPORTED_METHODS,
            events = SUPPORTED_EVENTS,
            accounts = accounts,
        )
        val approved = WalletKit.generateApprovedNamespaces(proposal, mapOf("eip155" to supported))
        WalletKit.approveSession(Wallet.Params.SessionApprove(proposalId, approved)) { error ->
            _events.tryEmit(WcEvent.OnError(error.throwable.message ?: "Approve failed"))
        }
    }

    actual suspend fun rejectSession(proposalId: String, reason: String) {
        WalletKit.rejectSession(Wallet.Params.SessionReject(proposalId, reason)) { error ->
            _events.tryEmit(WcEvent.OnError(error.throwable.message ?: "Reject failed"))
        }
    }

    actual suspend fun approvedChains(topic: String): Set<Long> {
        // Read from WalletKit's persisted session store (survives app restart) — not a freshly-settled cache.
        // getActiveSessionByTopic throws IllegalStateException if WalletKit isn't initialized; treat as none.
        val session = runCatching { WalletKit.getActiveSessionByTopic(topic) }.getOrNull() ?: return emptySet()
        val namespaces = session.namespaces.values
        return approvedChainIdsFrom(
            chains = namespaces.flatMap { it.chains ?: emptyList() },
            accounts = namespaces.flatMap { it.accounts },
        )
    }

    actual suspend fun respondRequest(requestId: Long, topic: String, result: String) {
        val response = Wallet.Params.SessionRequestResponse(
            sessionTopic = topic,
            jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcResult(id = requestId, result = result),
        )
        WalletKit.respondSessionRequest(response) { error ->
            _events.tryEmit(WcEvent.OnError(error.throwable.message ?: "Respond failed"))
        }
    }

    actual suspend fun rejectRequest(requestId: Long, topic: String, reason: String) {
        val response = Wallet.Params.SessionRequestResponse(
            sessionTopic = topic,
            jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcError(id = requestId, code = 4001, message = reason),
        )
        WalletKit.respondSessionRequest(response) { error ->
            _events.tryEmit(WcEvent.OnError(error.throwable.message ?: "Reject request failed"))
        }
    }

    actual suspend fun disconnect(topic: String) {
        WalletKit.disconnectSession(Wallet.Params.SessionDisconnect(topic)) { error ->
            _events.tryEmit(WcEvent.OnError(error.throwable.message ?: "Disconnect failed"))
        }
    }

    private companion object {
        val SUPPORTED_METHODS = listOf("eth_sendTransaction", "personal_sign", "eth_signTypedData_v4")
        val SUPPORTED_EVENTS = listOf("chainChanged", "accountsChanged")
    }
}

private fun Wallet.Model.SessionProposal.toWcProposal(verifyContext: Wallet.Model.VerifyContext): WcSessionProposal {
    val namespaces = requiredNamespaces.values + optionalNamespaces.values
    return WcSessionProposal(
        proposalId = proposerPublicKey,
        dapp = WcDappMetadata(name, description, url, icons.map { it.toString() }, verifyContext.validation.name),
        chains = namespaces.flatMap { it.chains ?: emptyList() }.distinct(),
        methods = namespaces.flatMap { it.methods }.distinct(),
        events = namespaces.flatMap { it.events }.distinct(),
    )
}

private fun Wallet.Model.SessionRequest.toWcRequest(verifyContext: Wallet.Model.VerifyContext): WcSessionRequest =
    WcSessionRequest(
        requestId = request.id,
        topic = topic,
        chainId = chainId ?: "",
        method = request.method,
        params = request.params,
        dapp = WcDappMetadata(
            name = peerMetaData?.name ?: "",
            description = peerMetaData?.description ?: "",
            url = peerMetaData?.url ?: "",
            icons = peerMetaData?.icons?.map { it.toString() } ?: emptyList(),
            verifyContext = verifyContext.validation.name,
        ),
    )
