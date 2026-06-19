package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.KeychainSecureKeyStore
import com.tneff.cyppie.storage.SeedSession
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/**
 * iOS biometric unlock (KAN-101, ADR-0009) — Face/Touch ID via the biometry-gated Keychain (KAN-80).
 * Unlike Android (which drives a `BiometricPrompt` + auth-bound Keystore cipher), the iOS Keychain item
 * is guarded by `SecAccessControl(.biometryCurrentSet)`, so the **system** presents Face/Touch ID during
 * the keychain read inside [SeedVault.unlockWithBiometrics] — no app prompt. On success the decrypted
 * source becomes the shared [SeedSession] (same end state as the password path); the password stays the
 * always-available fallback. Enrollment is set up by `BiometricSupport` (ONB-9).
 */
@OptIn(ExperimentalForeignApi::class)
actual class BiometricUnlock {

    /**
     * Whether biometrics can be evaluated on this device (hardware present + enrolled). A precise check
     * that the wallet's keychain credential exists (without prompting) is a follow-up; if it's absent,
     * [unlock] degrades to [BiometricUnlockResult.Unavailable] and the password gate still works.
     */
    actual fun available(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

    actual suspend fun unlock(): BiometricUnlockResult =
        try {
            // Keychain read (presents Face/Touch ID) + PBKDF2/AES-GCM decrypt off the main thread.
            val source = withContext(Dispatchers.Default) {
                SeedVault(CiphertextStore.defaultFile(), KeychainSecureKeyStore()).unlockWithBiometrics()
            }
            SeedSession.set(source)
            BiometricUnlockResult.Success
        } catch (e: StorageException.InvalidPassword) {
            BiometricUnlockResult.WrongPassword // stored credential stale vs. the seed record (shouldn't happen)
        } catch (e: StorageException.NotInitialized) {
            BiometricUnlockResult.Error // a wallet exists check failed (no seed record)
        } catch (e: StorageException.KeyStoreUnavailable) {
            // No enrolled credential, or the user cancelled / biometric failed — keep the biometric
            // affordance and let the user retry or use the password (Cancelled, not a hard Unavailable).
            BiometricUnlockResult.Cancelled
        } catch (e: Throwable) {
            BiometricUnlockResult.Error
        }
}

@Composable
actual fun rememberBiometricUnlock(): BiometricUnlock = remember { BiometricUnlock() }
