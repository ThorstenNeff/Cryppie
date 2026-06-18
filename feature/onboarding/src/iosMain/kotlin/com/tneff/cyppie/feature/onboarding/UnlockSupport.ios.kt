package com.tneff.cyppie.feature.onboarding

import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SeedSession
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object UnlockSupport {
    actual val available: Boolean = true

    actual val isUnlocked: Boolean get() = SeedSession.isActive

    actual suspend fun unlock(password: String): UnlockOutcome {
        val pw = password.toCharArray()
        return try {
            // KDF (PBKDF2 210k) + AES-GCM decrypt off the main thread. The unlocked source becomes the
            // shared session (KAN-103) consumed by the wallet shell; cleared by lock() on auto-lock.
            val source = withContext(Dispatchers.Default) {
                SeedVault(CiphertextStore.defaultFile()).unlock(pw)
            }
            SeedSession.set(source)
            UnlockOutcome.Success
        } catch (e: StorageException.InvalidPassword) {
            UnlockOutcome.WrongPassword
        } catch (e: StorageException.NotInitialized) {
            UnlockOutcome.NoWallet
        } catch (e: Throwable) {
            UnlockOutcome.Error
        } finally {
            pw.fill(' ')
        }
    }

    actual fun lock() {
        SeedSession.clear()
    }
}
