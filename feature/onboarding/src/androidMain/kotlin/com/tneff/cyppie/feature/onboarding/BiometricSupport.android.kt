package com.tneff.cyppie.feature.onboarding

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tneff.cyppie.storage.AndroidSecureKeyStore
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_later
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_bio_title
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import javax.crypto.Cipher

/**
 * Android biometric enrollment (ONB-9): drives the system [BiometricPrompt] with a `CryptoObject`
 * wrapping the Keystore enroll cipher from `:storage` (`AndroidSecureKeyStore`, KAN-80). On a
 * successful prompt the authenticated cipher wraps the app password into the (hardware, auth-bound)
 * Keystore entry — the alias stays encapsulated in `:storage`. Needs a [FragmentActivity] host.
 */
actual class BiometricSupport(private val activity: FragmentActivity) {

    private val keyStore = AndroidSecureKeyStore(activity.applicationContext)

    actual fun availability(): BiometricAvailability =
        when (BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricAvailability.NoHardware
            else -> BiometricAvailability.PermissionDenied
        }

    actual suspend fun enable(password: String): BiometricEnableResult {
        val enrollCipher = try {
            keyStore.biometricEnrollCipher()
        } catch (e: Throwable) {
            return BiometricEnableResult.Unavailable
        }
        val authenticated = prompt(enrollCipher) ?: return BiometricEnableResult.AuthFailed
        val pwBytes = password.encodeToByteArray()
        return try {
            keyStore.enableBiometricUnlock(pwBytes, authenticated)
            BiometricEnableResult.Success
        } catch (e: Throwable) {
            BiometricEnableResult.LinkFailed
        } finally {
            pwBytes.fill(0)
        }
    }

    // Android doesn't expose the modality (face/fingerprint) pre-auth → a generic name (KAN-101 i18n-Low).
    actual fun biometryTypeName(): String = "Biometrics"

    /** Shows the system prompt; returns the biometric-authenticated cipher, or null on error/cancel. */
    private suspend fun prompt(cipher: Cipher): Cipher? {
        val title = getString(Res.string.onb_bio_title)
        val negative = getString(Res.string.onb_bio_later)
        return suspendCancellableCoroutine { cont ->
            val biometricPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resumeWith(Result.success(result.cryptoObject?.cipher))
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negative)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        }
    }
}

@Composable
actual fun rememberBiometricSupport(): BiometricSupport {
    val activity = LocalContext.current.findFragmentActivity()
    return remember(activity) { BiometricSupport(activity) }
}

private fun Context.findFragmentActivity(): FragmentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("No FragmentActivity host found for BiometricPrompt")
}
