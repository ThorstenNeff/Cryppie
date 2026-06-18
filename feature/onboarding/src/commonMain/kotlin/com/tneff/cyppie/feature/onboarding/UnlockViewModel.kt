package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Inline error on the unlock field/banner (KAN-92). Reveals nothing beyond "wrong". */
enum class UnlockError { Empty, Wrong, Failed }

/**
 * Returning-user unlock flow controller (KAN-92): password → [UnlockSupport.unlock] → `SeedSource`.
 * Wrong passwords increment a counter; from the 5th, exponential backoff ([unlockLockoutSeconds])
 * disables input with a live countdown (no auto-wipe). The password is never logged; it is held only
 * as transient screen state and handed to the seam, which zeroizes its `CharArray`.
 */
class UnlockViewModel : ViewModel() {
    var password: String by mutableStateOf("")
        private set
    var error: UnlockError? by mutableStateOf(null)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var failures: Int by mutableStateOf(0)
        private set
    var lockoutRemaining: Long by mutableStateOf(0L)
        private set

    private var countdown: Job? = null

    val lockedOut: Boolean get() = lockoutRemaining > 0L

    fun updatePassword(value: String) {
        password = value
        if (error == UnlockError.Wrong || error == UnlockError.Empty) error = null
    }

    fun submit(onUnlocked: () -> Unit) {
        if (busy || lockedOut) return
        if (password.isBlank()) {
            error = UnlockError.Empty
            return
        }
        busy = true
        viewModelScope.launch {
            when (UnlockSupport.unlock(password)) {
                UnlockOutcome.Success -> {
                    password = ""
                    failures = 0
                    error = null
                    onUnlocked()
                }
                UnlockOutcome.WrongPassword -> {
                    failures += 1
                    error = UnlockError.Wrong
                    maybeStartLockout()
                }
                UnlockOutcome.NoWallet, UnlockOutcome.Error -> error = UnlockError.Failed
            }
            busy = false
        }
    }

    private fun maybeStartLockout() {
        val seconds = unlockLockoutSeconds(failures)
        if (seconds <= 0L) return
        countdown?.cancel()
        lockoutRemaining = seconds
        countdown = viewModelScope.launch {
            while (lockoutRemaining > 0L) {
                delay(1000)
                lockoutRemaining -= 1L
            }
        }
    }
}
