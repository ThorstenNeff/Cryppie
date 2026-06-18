package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// no biometrics on this target — password is always the unlock fallback (SPEC_UNLOCK).
actual class BiometricUnlock {
    actual fun available(): Boolean = false
    actual suspend fun unlock(): BiometricUnlockResult = BiometricUnlockResult.Unavailable
}

@Composable
actual fun rememberBiometricUnlock(): BiometricUnlock = remember { BiometricUnlock() }
