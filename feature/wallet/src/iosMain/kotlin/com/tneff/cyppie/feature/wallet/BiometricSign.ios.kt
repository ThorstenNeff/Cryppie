package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.wallet.SeedSource

/**
 * iOS biometric Send re-auth — wired after KAN-101 merges (reuse the `:storage`
 * `SeedVault.unlockWithBiometrics()` Keychain path). Unavailable until then; the password gate covers iOS.
 */
actual class BiometricSign {
    actual fun available(): Boolean = false
    actual suspend fun authorize(): SeedSource? = null
}

@Composable
actual fun rememberBiometricSign(): BiometricSign = remember { BiometricSign() }
