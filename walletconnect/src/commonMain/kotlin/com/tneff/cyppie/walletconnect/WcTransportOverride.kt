package com.tneff.cyppie.walletconnect

/**
 * Debug-only activation seam (KAN-126 L3 / KAN-127 harness). The app shell reads [transport]; if non-null
 * it uses that [WcTransport] instead of the real [WalletConnectController]. It is set **only** by the
 * debug build's `cyppie://wc-e2e` deep-link handler (which builds a `FakeWalletConnectController` from
 * `:walletconnect-e2e`, a `debugImplementation`-only module). In a release build nothing ever sets it —
 * the Fake module isn't in the binary and the deep-link scheme isn't registered — so it stays null and
 * the real transport is always used. Inert (a nullable holder) in production.
 */
object WcTransportOverride {
    var transport: WcTransport? = null
}
