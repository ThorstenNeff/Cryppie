package com.tneff.cyppie

import androidx.compose.runtime.Composable

// Web has no local wallet (read-only) — Home is never routed here.
@Composable
internal actual fun WalletShellRoot(onLock: () -> Unit) {}
