package com.tneff.cyppie.feature.onboarding

import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.SecureSeedSource
import com.tneff.cyppie.storage.SeedVault
import com.tneff.cyppie.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object UnlockSupport {
    actual val available: Boolean = true

    // The unlocked in-memory seed session (held for the wallet repo; cleared on auto-lock).
    private var session: SecureSeedSource? = null

    actual val isUnlocked: Boolean get() = session != null

    actual suspend fun unlock(password: String): UnlockOutcome {
        val pw = password.toCharArray()
        return try {
            // KDF (PBKDF2 210k) + AES-GCM decrypt off the main thread.
            val source = withContext(Dispatchers.Default) {
                SeedVault(CiphertextStore.defaultFile()).unlock(pw)
            }
            session?.close()
            session = source
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
        session?.close()
        session = null
    }
}
