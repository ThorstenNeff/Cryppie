package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable

/** Whether biometric unlock can be offered on this device (ONB-9). */
enum class BiometricAvailability { Available, NotEnrolled, NoHardware, PermissionDenied }

/** Result of attempting to enable biometric unlock (ONB-9). */
enum class BiometricEnableResult { Success, AuthFailed, PermissionDenied, LinkFailed, Unavailable }

/**
 * Onboarding's seam to platform biometrics + the `:storage` biometric-unlock enrollment (ONB-9).
 * Like the other seams it is non-web (`:storage`/`:wallet` have no js/wasm); web/desktop report
 * [BiometricAvailability.NoHardware]. Biometrics is **optional** — the app password (ONB-8) is always
 * the fallback gate, so [enable] failing never blocks finishing onboarding.
 *
 * [enable] runs the native prompt (Android `BiometricPrompt`, iOS Face/Touch ID) and, on success,
 * links the stored wallet to biometric unlock via `SeedVault` (using the app [password] captured in
 * the flow). The actual enrollment path is shared with `:storage` so the keystore alias stays
 * encapsulated (KAN-75 cross-agent seam).
 */
expect class BiometricSupport {
    fun availability(): BiometricAvailability
    suspend fun enable(password: String): BiometricEnableResult

    /**
     * The user-facing name of this device's biometric method, for the enable-screen copy
     * (`onb_bio_body` "Unlock your wallet with %1$s") — KAN-101 i18n-Low. iOS returns the Apple brand
     * ("Face ID"/"Touch ID"/"Optic ID", intentionally untranslated); other platforms a generic
     * "Biometrics".
     */
    fun biometryTypeName(): String
}

/** Provides a platform [BiometricSupport] (Android needs the host Activity, hence a composable factory). */
@Composable
expect fun rememberBiometricSupport(): BiometricSupport
