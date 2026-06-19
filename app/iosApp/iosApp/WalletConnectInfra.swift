import Foundation
import WalletConnectRelay
import WalletConnectSigner

// KAN-121 — the reown infra impls the app must supply to `Networking.configure`/`WalletKit.configure`.
// Kept dependency-free (no Starscream / Web3 / CryptoSwift SPM): the socket runs on the native
// URLSessionWebSocketTask; keccak256 is vendored. recoverPubKey is unused on our path (we sign OUTbound
// WC requests; we don't verify inbound SIWE/auth signatures) → it throws, documented.

// MARK: - WebSocket (native URLSessionWebSocketTask, no Starscream)

final class URLSessionWebSocket: NSObject, WebSocketConnecting, URLSessionWebSocketDelegate {
    var request: URLRequest
    private(set) var isConnected: Bool = false
    var onConnect: (() -> Void)?
    var onDisconnect: ((Error?) -> Void)?
    var onText: ((String) -> Void)?

    private var task: URLSessionWebSocketTask?
    private lazy var session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)

    init(request: URLRequest) { self.request = request; super.init() }

    func connect() {
        let task = session.webSocketTask(with: request)
        self.task = task
        task.resume()
        receive()
    }

    func disconnect() {
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
        isConnected = false
    }

    func write(string: String, completion: (() -> Void)?) {
        task?.send(.string(string)) { _ in completion?() }
    }

    private func receive() {
        task?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                if case .string(let text) = message { self.onText?(text) }
                self.receive()
            case .failure(let error):
                self.isConnected = false
                self.onDisconnect?(error)
            }
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        isConnected = true
        onConnect?()
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        isConnected = false
        onDisconnect?(nil)
    }
}

final class URLSessionWebSocketFactory: WebSocketFactory {
    func create(with url: URL) -> WebSocketConnecting {
        URLSessionWebSocket(request: URLRequest(url: url))
    }
}

// MARK: - CryptoProvider (vendored keccak256; recover unused on our path)

struct CyppieCryptoProvider: CryptoProvider {
    func recoverPubKey(signature: EthereumSignature, message: Data) throws -> Data {
        // Only needed to verify inbound SIWE/auth signatures, which Cyppie's WC integration doesn't use
        // (we sign outbound requests in Kotlin and respond with the result). Throw rather than ship a
        // wrong recovery. If SIWE/auth is enabled later, add a vetted secp256k1 recovery (Web3.swift).
        throw NSError(domain: "Cyppie.Crypto", code: -1,
                      userInfo: [NSLocalizedDescriptionKey: "recoverPubKey not supported (SIWE/auth unused)"])
    }

    func keccak256(_ data: Data) -> Data { Keccak256.hash(data) }
}

// MARK: - Keccak-256 (Ethereum variant, 0x01 padding) — vendored, dependency-free

enum Keccak256 {
    static func hash(_ input: Data) -> Data {
        let rate = 136 // 1088-bit rate (capacity 512), 32-byte output
        var state = [UInt64](repeating: 0, count: 25)
        var message = [UInt8](input)
        message.append(0x01)
        while message.count % rate != 0 { message.append(0) }
        message[message.count - 1] |= 0x80

        var offset = 0
        while offset < message.count {
            for i in 0..<(rate / 8) {
                var lane: UInt64 = 0
                for j in 0..<8 { lane |= UInt64(message[offset + i * 8 + j]) << (UInt64(8 * j)) }
                state[i] ^= lane
            }
            permute(&state)
            offset += rate
        }

        var out = [UInt8]()
        for i in 0..<4 {
            let lane = state[i]
            for j in 0..<8 { out.append(UInt8((lane >> UInt64(8 * j)) & 0xff)) }
        }
        return Data(out)
    }

    private static let roundConstants: [UInt64] = [
        0x0000000000000001, 0x0000000000008082, 0x800000000000808a, 0x8000000080008000,
        0x000000000000808b, 0x0000000080000001, 0x8000000080008081, 0x8000000000008009,
        0x000000000000008a, 0x0000000000000088, 0x0000000080008009, 0x000000008000000a,
        0x000000008000808b, 0x800000000000008b, 0x8000000000008089, 0x8000000000008003,
        0x8000000000008002, 0x8000000000000080, 0x000000000000800a, 0x800000008000000a,
        0x8000000080008081, 0x8000000000008080, 0x0000000080000001, 0x8000000080008008,
    ]
    private static let rotations: [Int] = [
        1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44,
    ]
    private static let piLane: [Int] = [
        10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1,
    ]

    private static func rotl(_ x: UInt64, _ n: Int) -> UInt64 { (x << UInt64(n)) | (x >> UInt64(64 - n)) }

    private static func permute(_ a: inout [UInt64]) {
        for round in 0..<24 {
            // θ
            var c = [UInt64](repeating: 0, count: 5)
            for x in 0..<5 { c[x] = a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20] }
            for x in 0..<5 {
                let d = c[(x + 4) % 5] ^ rotl(c[(x + 1) % 5], 1)
                var y = 0
                while y < 25 { a[y + x] ^= d; y += 5 }
            }
            // ρ + π
            var t = a[1]
            for i in 0..<24 {
                let j = piLane[i]
                let tmp = a[j]
                a[j] = rotl(t, rotations[i])
                t = tmp
            }
            // χ
            var y = 0
            while y < 25 {
                var row = [UInt64](repeating: 0, count: 5)
                for x in 0..<5 { row[x] = a[y + x] }
                for x in 0..<5 { a[y + x] = row[x] ^ ((~row[(x + 1) % 5]) & row[(x + 2) % 5]) }
                y += 5
            }
            // ι
            a[0] ^= roundConstants[round]
        }
    }
}
