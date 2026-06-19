package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.KeychainSecureKeyStore
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import kotlinx.cinterop.ExperimentalForeignApi
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeOpticID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/**
 * iOS biometric enrollment via the Keychain (`SecAccessControl(.biometryCurrentSet)`): storing the
 * app password under the biometric-guarded item is the enrollment; the system presents Face/Touch ID
 * at unlock (no app prompt — unlike Android). The keychain item is independent of the seed file, so
 * the in-memory store here is unused by `enableBiometricUnlock`. Availability defaults to Available;
 * a precise `LAContext.canEvaluatePolicy` check is a follow-up (no biometrics → enroll fails → the
 * screen offers continue-without, and the password gate still works).
 */
actual class BiometricSupport {
    actual fun availability(): BiometricAvailability = BiometricAvailability.Available

    actual suspend fun enable(password: String): BiometricEnableResult {
        val pw = password.toCharArray()
        return try {
            SeedVault(CiphertextStore.inMemory(), KeychainSecureKeyStore()).enableBiometricUnlock(pw)
            BiometricEnableResult.Success
        } catch (e: StorageException.KeyStoreUnavailable) {
            BiometricEnableResult.Unavailable
        } catch (e: Throwable) {
            BiometricEnableResult.LinkFailed
        } finally {
            pw.fill(' ')
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun biometryTypeName(): String {
        // biometryType is only populated after canEvaluatePolicy is queried on the context.
        val ctx = LAContext()
        ctx.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)
        return when (ctx.biometryType) {
            LABiometryTypeFaceID -> "Face ID"
            LABiometryTypeTouchID -> "Touch ID"
            LABiometryTypeOpticID -> "Optic ID"
            else -> "Biometrics"
        }
    }
}

@Composable
actual fun rememberBiometricSupport(): BiometricSupport = remember { BiometricSupport() }
