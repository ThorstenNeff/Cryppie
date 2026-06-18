package com.tneff.cyppie.walletconnect

/** Sealed failures for the WalletConnect transport. */
sealed class WalletConnectException(message: String) : Exception(message) {

    /** WalletConnect is not available on this platform (Desktop/Web) or not yet wired. */
    class Unsupported(message: String = "WalletConnect is not available on this platform") :
        WalletConnectException(message)

    /** Pairing failed (bad/expired `wc:` URI, relay error). */
    class Pairing(message: String) : WalletConnectException(message)

    /** A requested method/chain is not supported by the wallet. */
    class UnsupportedRequest(message: String) : WalletConnectException(message)
}
