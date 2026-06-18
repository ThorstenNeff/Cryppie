package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Desktop/Web: no biometric hardware — the app password remains the unlock gate.
actual class BiometricSupport {
    actual fun availability(): BiometricAvailability = BiometricAvailability.NoHardware
    actual suspend fun enable(password: String): BiometricEnableResult = BiometricEnableResult.Unavailable
}

@Composable
actual fun rememberBiometricSupport(): BiometricSupport = remember { BiometricSupport() }
