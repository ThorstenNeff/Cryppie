import Foundation
import Combine
import ReownWalletKit
import Shared

/**
 KAN-121 — the iOS WalletConnect shim. reown-swift (ReownWalletKit) is a Swift Package not consumable
 from Kotlin/Native, so `:walletconnect`'s iOS `actual` delegates to this `WalletConnectBridge`
 implementation (exported from the `Shared` framework, KAN-114-Et4). This forwards the wallet's
 commands to WalletKit and pushes WalletKit's delegate events back into Kotlin via `WalletConnectIos`.

 🔒 Key-path invariant (ADR-0015): only transport data + the **signature/tx-hash result string** cross
 this boundary — never the seed/private key. Signing stays in `:wallet` (the Kotlin app decodes,
 discloses, signs via WalletConnectSigner, then calls respondRequest with the result).

 Setup (in iOSApp): call `WalletConnectShim.install()` once after `WalletKit.configure(...)`.
 */
final class WalletConnectShim: WalletConnectBridge {

    private var cancellables = Set<AnyCancellable>()
    private let ios = WalletConnectIos.shared

    /// Wire WalletKit's publishers → Kotlin, and register self as the bridge. Call once at launch.
    func install() {
        ios.bridge = self

        WalletKit.instance.sessionProposalPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] proposal, context in
                guard let self = self else { return }
                let p = proposal.proposer
                self.ios.emitSessionProposal(
                    proposalId: proposal.id,
                    dappName: p.name,
                    dappDescription: p.description,
                    dappUrl: p.url,
                    chains: proposal.requiredNamespaces.values.flatMap { $0.chains?.map { $0.absoluteString } ?? [] },
                    methods: Array(proposal.requiredNamespaces.values.flatMap { $0.methods }),
                    verifyContext: context?.validation.rawValue
                )
            }
            .store(in: &cancellables)

        WalletKit.instance.sessionRequestPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] request, context in
                guard let self = self else { return }
                self.ios.emitSessionRequest(
                    requestId: Int64(request.id.right ?? 0),
                    topic: request.topic,
                    chainId: request.chainId.absoluteString,
                    method: request.method,
                    params: request.params.encodedJsonString(),
                    dappName: "",
                    dappUrl: "",
                    verifyContext: context?.validation.rawValue
                )
            }
            .store(in: &cancellables)

        WalletKit.instance.sessionSettlePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] session in self?.ios.emitSessionSettled(topic: session.topic) }
            .store(in: &cancellables)
    }

    // MARK: - WalletConnectBridge

    func pair(uri: String) {
        runCatching("pair") {
            guard let wcUri = WalletConnectURI(string: uri) else { throw ShimError.badUri }
            try await WalletKit.instance.pair(uri: wcUri)
        }
    }

    func approveSession(proposalId: String, accounts: [String]) {
        runCatching("approve") {
            let accs = accounts.compactMap { Account($0) }
            let chains = Array(Set(accs.map { $0.blockchain }))
            let ns = SessionNamespace(
                chains: chains,
                accounts: accs,
                methods: ["eth_sendTransaction", "personal_sign", "eth_signTypedData_v4"],
                events: ["chainChanged", "accountsChanged"]
            )
            _ = try await WalletKit.instance.approve(proposalId: proposalId, namespaces: ["eip155": ns])
        }
    }

    func rejectSession(proposalId: String, reason: String) {
        runCatching("reject") { try await WalletKit.instance.rejectSession(proposalId: proposalId, reason: .userRejected) }
    }

    func respondRequest(requestId: Int64, topic: String, result: String) {
        runCatching("respond") {
            try await WalletKit.instance.respond(topic: topic, requestId: RPCID(requestId), response: .response(AnyCodable(result)))
        }
    }

    func rejectRequest(requestId: Int64, topic: String, reason: String) {
        runCatching("rejectRequest") {
            let err = JSONRPCError(code: 4001, message: reason)
            try await WalletKit.instance.respond(topic: topic, requestId: RPCID(requestId), response: .error(err))
        }
    }

    func disconnect(topic: String) {
        runCatching("disconnect") { try await WalletKit.instance.disconnect(topic: topic) }
    }

    /// Approved chain refs (CAIP-2) for [topic], from WalletKit's persisted session store. The Kotlin
    /// side parses & filters to EVM chain ids — keeping chain-id parsing single-sourced in Kotlin.
    func approvedChains(topic: String) -> [String] {
        guard let session = WalletKit.instance.getSessions().first(where: { $0.topic == topic }) else { return [] }
        return session.namespaces.values.flatMap { ($0.chains ?? []).map { $0.absoluteString } }
    }

    /// Approved CAIP-10 accounts for [topic]; Kotlin extracts the EvmAddress.
    func approvedAccounts(topic: String) -> [String] {
        guard let session = WalletKit.instance.getSessions().first(where: { $0.topic == topic }) else { return [] }
        return session.namespaces.values.flatMap { $0.accounts.map { $0.absoluteString } }
    }

    // MARK: - Helpers

    private enum ShimError: Error { case badUri }

    /// Bridge methods are synchronous (the Kotlin contract); reown's are async. Run on a Task and surface
    /// failures to Kotlin as WC errors (never crash the bridge).
    private func runCatching(_ op: String, _ block: @escaping () async throws -> Void) {
        Task {
            do { try await block() }
            catch { ios.emitError(message: "\(op): \(error.localizedDescription)") }
        }
    }
}

private extension AnyCodable {
    /// reown params arrive as AnyCodable; the Kotlin side wants the raw JSON-RPC params string.
    func encodedJsonString() -> String {
        (try? JSONEncoder().encode(self)).flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
    }
}
