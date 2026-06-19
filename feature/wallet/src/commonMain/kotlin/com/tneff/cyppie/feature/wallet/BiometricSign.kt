package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.Composable
import com.tneff.cyppie.wallet.SeedSource

/**
 * Biometric re-auth for the Send sign step (KAN-119, ADR-0009 / fast-follow to KAN-110's password
 * re-auth). On success returns a **fresh per-sign** [SeedSource] (decrypted via the platform biometric
 * → keystore/Keychain), which `SendOrchestrator.signAndBroadcast` signs with and zeroizes immediately
 * (M1). Returns null on cancel / failure / unavailable — **fail-closed** (no source ⇒ no sign), and the
 * password gate stays the always-available fallback. Android = BiometricPrompt + auth-bound Keystore;
 * iOS = the Keychain biometry path (wired with KAN-74/101); Desktop = unavailable.
 */
expect class BiometricSign {
    /** True only when biometrics are available AND a Send-unlock credential is enrolled. */
    fun available(): Boolean

    /** Runs the biometric prompt; on success a fresh signing [SeedSource], else null (cancel/fail). */
    suspend fun authorize(): SeedSource?
}

/** Provides the platform [BiometricSign] (Android needs the FragmentActivity host). */
@Composable
expect fun rememberBiometricSign(): BiometricSign
