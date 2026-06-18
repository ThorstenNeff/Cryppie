package com.tneff.cyppie

import androidx.compose.runtime.Composable

/**
 * App-shell Home destination (KAN-103): renders the live wallet shell on targets that can hold an
 * unlocked seed (android/ios/jvm → `:feature:wallet` `WalletShell`); web has no local wallet and is
 * never routed here. [onLock] re-routes to the unlock screen (lost/expired session).
 */
@Composable
internal expect fun WalletShellRoot(onLock: () -> Unit)
