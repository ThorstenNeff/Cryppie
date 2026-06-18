package com.tneff.cyppie

import androidx.compose.runtime.Composable
import com.tneff.cyppie.feature.wallet.WalletShell

@Composable
internal actual fun WalletShellRoot(onLock: () -> Unit) = WalletShell(onLock = onLock)
