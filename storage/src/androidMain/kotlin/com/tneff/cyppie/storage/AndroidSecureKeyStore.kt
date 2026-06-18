package com.tneff.cyppie.storage

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed [SecureKeyStore]. The AES-GCM key is generated **inside AndroidKeyStore**
 * with `setUserAuthenticationRequired(true)`, so every use must be authorized by a
 * `BiometricPrompt`-authenticated `Cipher`. `:storage` owns the key + wrap/unwrap + the persistence
 * of the wrapped secret (the IV/ciphertext in `SharedPreferences`); it shows **no UI**.
 *
 * ## Cross-agent seam (ONB-9 / Dev-1 supplies the `BiometricPrompt` UI)
 * The plain [protect]/[retrieve]`(alias)` overloads throw — on Android the app must drive biometrics:
 *
 * **Enroll** (after the password is known):
 * ```
 * val cipher = keyStore.encryptCipher(alias)
 * BiometricPrompt(activity, ...).authenticate(PromptInfo(...), CryptoObject(cipher))
 * // onAuthenticationSucceeded(result):
 * keyStore.protect(alias, passwordBytes, result.cryptoObject!!.cipher!!)
 * ```
 * **Unlock**:
 * ```
 * val cipher = keyStore.decryptCipher(alias)
 * BiometricPrompt(activity, ...).authenticate(PromptInfo(...), CryptoObject(cipher))
 * // onAuthenticationSucceeded(result):
 * val passwordBytes = keyStore.retrieve(alias, result.cryptoObject!!.cipher!!)  // → SeedVault.unlock
 * ```
 * Dev-1 provides: the `Activity`/`Fragment` context for `BiometricPrompt` and routes the resulting
 * authenticated `Cipher` back into [protect]/[retrieve]. `:storage` provides everything else.
 */
class AndroidSecureKeyStore(private val context: Context) : SecureKeyStore {

    override val isHardwareBacked: Boolean = true

    /** ENCRYPT-mode [Cipher] under the auth-bound Keystore key — wrap in a `BiometricPrompt.CryptoObject`. */
    fun encryptCipher(alias: String): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias)) }

    /** DECRYPT-mode [Cipher] (with the stored IV) under the Keystore key — wrap in a `CryptoObject`. */
    fun decryptCipher(alias: String): Cipher {
        val key = getKey(alias) ?: throw StorageException.KeyStoreUnavailable("No key for '$alias'")
        val iv = loadIv(alias) ?: throw StorageException.KeyStoreUnavailable("No IV for '$alias'")
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
    }

    /** Wraps [secret] with the biometric-[authenticatedCipher] and persists IV+ciphertext. */
    fun protect(alias: String, secret: ByteArray, authenticatedCipher: Cipher) {
        val ciphertext = authenticatedCipher.doFinal(secret)
        store(alias, authenticatedCipher.iv, ciphertext)
    }

    /** Unwraps the stored secret with the biometric-[authenticatedCipher]. */
    fun retrieve(alias: String, authenticatedCipher: Cipher): ByteArray {
        val ct = loadCt(alias) ?: throw StorageException.KeyStoreUnavailable("No entry for '$alias'")
        return authenticatedCipher.doFinal(ct)
    }

    override suspend fun protect(alias: String, secret: ByteArray): Unit =
        throw StorageException.KeyStoreUnavailable("Android needs a BiometricPrompt Cipher — use protect(alias, secret, cipher)")

    override suspend fun retrieve(alias: String): ByteArray? =
        throw StorageException.KeyStoreUnavailable("Android needs a BiometricPrompt Cipher — use retrieve(alias, cipher)")

    override suspend fun clear(alias: String) {
        runCatching { androidKeyStore().deleteEntry(alias) }
        prefs().edit().remove(ivPref(alias)).remove(ctPref(alias)).apply()
    }

    private fun getOrCreateKey(alias: String): SecretKey = getKey(alias) ?: run {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1) // per-use auth on API 24–29
                }
            }
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    private fun getKey(alias: String): SecretKey? =
        (androidKeyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun store(alias: String, iv: ByteArray, ciphertext: ByteArray) {
        prefs().edit()
            .putString(ivPref(alias), Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(ctPref(alias), Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    private fun loadIv(alias: String): ByteArray? =
        prefs().getString(ivPref(alias), null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    private fun loadCt(alias: String): ByteArray? =
        prefs().getString(ctPref(alias), null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ivPref(alias: String) = "$alias.iv"
    private fun ctPref(alias: String) = "$alias.ct"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFS = "cyppie_secure_keystore"
    }
}
