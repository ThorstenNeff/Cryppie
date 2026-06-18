package com.tneff.cyppie.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * KAN-80 round-trip for [AndroidSecureKeyStore] on the host. The real AndroidKeyStore HW key can't be
 * generated without a device, so a plain AES-GCM key stands in as the "fake HW" — exactly the seam
 * the wallet API uses, since `enableBiometricUnlock`/`retrieveUnlockPassword` take the authenticated
 * `Cipher` as a parameter (only the cipher *providers* touch AndroidKeyStore). Persistence is real
 * (Robolectric `SharedPreferences`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // Robolectric 4.14 tops out at SDK 35; project targetSdk is 36
class AndroidSecureKeyStoreTest {

    private val fakeHwKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun enrollCipher(): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, fakeHwKey) }

    private fun unlockCipher(iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, fakeHwKey, GCMParameterSpec(128, iv)) }

    private fun newStore() = AndroidSecureKeyStore(ApplicationProvider.getApplicationContext())

    @Test
    fun enrollThenUnlockRoundTripsThePassword() {
        val store = newStore()
        val password = "correct horse battery staple".encodeToByteArray()

        val enroll = enrollCipher()
        store.enableBiometricUnlock(password, enroll)

        // Dev-1 rebuilds the decrypt cipher via biometricUnlockCipher() (stored IV); here the same key + IV.
        val recovered = store.retrieveUnlockPassword(unlockCipher(enroll.iv))
        assertContentEquals(password, recovered)
    }

    @Test
    fun retrieveWithoutEnrollFailsClosed() {
        assertFailsWith<StorageException.KeyStoreUnavailable> {
            newStore().retrieveUnlockPassword(unlockCipher(ByteArray(12)))
        }
    }

    @Test
    fun disableRemovesTheEnrolledSecret() {
        val store = newStore()
        val enroll = enrollCipher()
        store.enableBiometricUnlock("pw".encodeToByteArray(), enroll)
        store.disableBiometricUnlock()
        assertFailsWith<StorageException.KeyStoreUnavailable> {
            store.retrieveUnlockPassword(unlockCipher(enroll.iv))
        }
    }
}
