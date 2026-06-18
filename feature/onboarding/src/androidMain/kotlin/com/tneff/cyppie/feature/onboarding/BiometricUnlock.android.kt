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
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.common_cancel
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_title
import com.tneff.cyppie.storage.AndroidSecureKeyStore
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import javax.crypto.Cipher

/**
 * Android biometric unlock (KAN-92): the system [BiometricPrompt] releases a `CryptoObject` cipher
 * that unwraps the app password from the auth-bound Keystore entry (`:storage`, KAN-80). The recovered
 * password then drives the normal [UnlockSupport.unlock] — same `SeedSource` session as the password
 * path. The password bytes are zeroized immediately. Needs a [FragmentActivity] host.
 */
actual class BiometricUnlock(private val activity: FragmentActivity) {

    private val keyStore = AndroidSecureKeyStore(activity.applicationContext)

    actual fun available(): Boolean {
        val canAuth = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) return false
        // An unlock credential is enrolled iff a decrypt cipher can be built for the unlock alias.
        return try {
            keyStore.biometricUnlockCipher()
            true
        } catch (e: Throwable) {
            false
        }
    }

    actual suspend fun unlock(): BiometricUnlockResult {
        val cipher = try {
            keyStore.biometricUnlockCipher()
        } catch (e: Throwable) {
            return BiometricUnlockResult.Unavailable
        }
        val authenticated = prompt(cipher) ?: return BiometricUnlockResult.Cancelled
        val pwBytes = try {
            keyStore.retrieveUnlockPassword(authenticated)
        } catch (e: Throwable) {
            return BiometricUnlockResult.Error
        }
        return try {
            when (UnlockSupport.unlock(pwBytes.decodeToString())) {
                UnlockOutcome.Success -> BiometricUnlockResult.Success
                UnlockOutcome.WrongPassword -> BiometricUnlockResult.WrongPassword
                else -> BiometricUnlockResult.Error
            }
        } finally {
            pwBytes.fill(0)
        }
    }

    /** Shows the system prompt; returns the authenticated cipher, or null on error/cancel. */
    private suspend fun prompt(cipher: Cipher): Cipher? {
        val title = getString(Res.string.unlock_title)
        val negative = getString(Res.string.common_cancel)
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
actual fun rememberBiometricUnlock(): BiometricUnlock {
    val activity = LocalContext.current.findUnlockFragmentActivity()
    return remember(activity) { BiometricUnlock(activity) }
}

private fun Context.findUnlockFragmentActivity(): FragmentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("No FragmentActivity host found for BiometricPrompt")
}
