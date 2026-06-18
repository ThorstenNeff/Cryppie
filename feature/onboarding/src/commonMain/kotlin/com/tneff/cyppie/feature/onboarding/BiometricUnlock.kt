package com.tneff.cyppie.feature.onboarding

/** Outcome of a biometric unlock attempt (KAN-92). Password stays the fallback on every non-success. */
enum class BiometricUnlockResult { Success, WrongPassword, Cancelled, Unavailable, Error }

/**
 * Biometric unlock seam (KAN-92): when the user enrolled biometrics in ONB-9, the system prompt
 * releases the app password from the auth-bound Keystore/Keychain, which then drives the normal
 * [UnlockSupport.unlock] (reuse — same `SeedSource` session). Android is fully supported; iOS biometric
 * unlock is a follow-up; jvm/web have no biometrics. Password bytes are zeroized after use.
 */
expect class BiometricUnlock {
    /** True only when biometrics are available AND an unlock credential was enrolled (ONB-9). */
    fun available(): Boolean

    suspend fun unlock(): BiometricUnlockResult
}

/** Obtains the platform [BiometricUnlock] (Android needs the FragmentActivity host). */
@androidx.compose.runtime.Composable
expect fun rememberBiometricUnlock(): BiometricUnlock
