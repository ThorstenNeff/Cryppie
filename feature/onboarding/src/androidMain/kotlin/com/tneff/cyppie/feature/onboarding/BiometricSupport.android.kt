package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// WIP (KAN-33): real biometric enrollment pending the shared :storage enroll path (Option A) +
// androidx.biometric (Android) / LAContext availability (iOS). Reports "unavailable" until wired,
// so the password gate (ONB-8) stays the unlock path and onboarding still completes.
actual class BiometricSupport {
    actual fun availability(): BiometricAvailability = BiometricAvailability.NoHardware
    actual suspend fun enable(password: String): BiometricEnableResult = BiometricEnableResult.Unavailable
}

@Composable
actual fun rememberBiometricSupport(): BiometricSupport = remember { BiometricSupport() }
