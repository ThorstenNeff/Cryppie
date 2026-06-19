package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.wallet.SeedSource

// Desktop has no biometrics — Send re-auth uses the password gate.
actual class BiometricSign {
    actual fun available(): Boolean = false
    actual suspend fun authorize(): SeedSource? = null
}

@Composable
actual fun rememberBiometricSign(): BiometricSign = remember { BiometricSign() }
