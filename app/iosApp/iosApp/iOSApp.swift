import SwiftUI
import ReownWalletKit
import WalletConnectNetworking

@main
struct iOSApp: App {
    init() { WalletConnectStartup.run() }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// KAN-121 — reown WalletConnect startup: configure Networking + WalletKit once, then install the shim
/// that bridges WalletKit's publishers into the KMP `:walletconnect` layer.
enum WalletConnectStartup {
    static func run() {
        // projectId is injected via Info.plist (Build Setting / xcconfig), NEVER hardcoded in the repo.
        let projectId = (Bundle.main.object(forInfoDictionaryKey: "WC_PROJECT_ID") as? String) ?? "WC_PROJECT_ID_PLACEHOLDER"
        // App Group for reown's shared storage — needs a matching App Group entitlement for runtime.
        let groupId = (Bundle.main.object(forInfoDictionaryKey: "WC_GROUP_ID") as? String)
            ?? "group.\(Bundle.main.bundleIdentifier ?? "app.cyppie")"

        Networking.configure(
            groupIdentifier: groupId,
            projectId: projectId,
            socketFactory: URLSessionWebSocketFactory()
        )

        let metadata = AppMetadata(
            name: "Cyppie",
            description: "Cyppie — non-custodial wallet",
            url: "https://cyppie.app",
            icons: ["https://cyppie.app/icon.png"],
            redirect: try! AppMetadata.Redirect(native: "cyppie://", universal: nil)
        )
        WalletKit.configure(metadata: metadata, crypto: CyppieCryptoProvider())

        WalletConnectShim().install()
    }
}
