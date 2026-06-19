package com.tneff.cyppie.feature.wallet

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.common_cancel
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_title
import com.tneff.cyppie.storage.AndroidSecureKeyStore
import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import javax.crypto.Cipher

/**
 * Android biometric Send re-auth (KAN-119): the system [BiometricPrompt] releases a `CryptoObject`
 * cipher that unwraps the app password from the auth-bound Keystore (`:storage`, KAN-80); that password
 * decrypts a **fresh per-sign** [SeedSource] (SeedVault.unlock) — which `signAndBroadcast` signs with
 * and zeroizes immediately (M1). The recovered password bytes + chars are zeroized at once. Mirrors the
 * onboarding unlock prompt but returns a one-shot signing source rather than opening the shared session.
 * Needs a [FragmentActivity] host. Returns null on cancel/error (fail-closed → no sign).
 */
actual class BiometricSign(private val activity: FragmentActivity) {

    private val keyStore = AndroidSecureKeyStore(activity.applicationContext)

    actual fun available(): Boolean {
        val canAuth = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) return false
        // A Send-unlock credential is enrolled iff a decrypt cipher can be built for the unlock alias.
        return try {
            keyStore.biometricUnlockCipher()
            true
        } catch (e: Throwable) {
            false
        }
    }

    actual suspend fun authorize(): SeedSource? {
        val cipher = try {
            keyStore.biometricUnlockCipher()
        } catch (e: Throwable) {
            return null
        }
        val authenticated = prompt(cipher) ?: return null // user cancelled / error
        val pwBytes = try {
            keyStore.retrieveUnlockPassword(authenticated)
        } catch (e: Throwable) {
            return null
        }
        val pwChars = pwBytes.decodeToString().toCharArray()
        return try {
            // Fresh per-sign source; closed/zeroized by SendOrchestrator.signAndBroadcast (M1).
            SeedVault(CiphertextStore.defaultFile()).unlock(pwChars)
        } catch (e: Throwable) {
            null
        } finally {
            pwChars.fill(' ')
            pwBytes.fill(0)
        }
    }

    /** Shows the system prompt; returns the authenticated cipher, or null on error/cancel. */
    private suspend fun prompt(cipher: Cipher): Cipher? {
        val title = getString(Res.string.send_auth_title)
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
actual fun rememberBiometricSign(): BiometricSign {
    val activity = LocalContext.current.findFragmentActivity()
    return remember(activity) { BiometricSign(activity) }
}

private fun Context.findFragmentActivity(): FragmentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("No FragmentActivity host found for BiometricPrompt")
}
