package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Desktop/Web: no biometric hardware — the app password remains the unlock gate.
actual class BiometricSupport {
    actual fun availability(): BiometricAvailability = BiometricAvailability.NoHardware
    actual suspend fun enable(password: String): BiometricEnableResult = BiometricEnableResult.Unavailable
    actual fun biometryTypeName(): String = "Biometrics" // no hardware — never shown, but the seam needs it
}

@Composable
actual fun rememberBiometricSupport(): BiometricSupport = remember { BiometricSupport() }
