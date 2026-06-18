package com.tneff.cyppie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingRoot
import com.tneff.cyppie.feature.onboarding.UnlockSupport
import com.tneff.cyppie.feature.onboarding.enableTestTagsAsResourceId
import com.tneff.cyppie.feature.onboarding.ui.UnlockScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Auto-lock delay after the app is backgrounded (KAN-92 / ADR-0009; dev-tunable). */
private const val AUTO_LOCK_MILLIS = 5 * 60 * 1000L

/** Top-level app destinations (KAN-89 app-shell skeleton). */
private enum class AppDestination { Resolving, Onboarding, Unlock, Home }

/**
 * App-shell launch routing (KAN-89): on start, [walletExists] (the shared `:storage` seed file via
 * the non-web seam, KAN-95) decides **onboarding** (no wallet) vs **unlock** (returning user), then
 * hands off to **Home**. The Unlock screen is still a stub until its UX (KAN-92), and Home shows a
 * placeholder until the live `WalletRepository` (unlocked `SeedSource` + RPC config) is wired. Web is
 * read-only / onboarding-only (`walletExists` = false there).
 */
@Composable
fun AppRoot() {
    CryptasaTheme {
        var destination by remember { mutableStateOf(AppDestination.Resolving) }

        LaunchedEffect(Unit) {
            destination = if (walletExists()) AppDestination.Unlock else AppDestination.Onboarding
        }

        // Auto-lock (KAN-92 / ADR-0009): on background, arm a 5-min timer; if the app doesn't return
        // in time, clear the in-memory seed session and require unlock again. Returning sooner cancels.
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        DisposableEffect(lifecycleOwner) {
            var lockJob: Job? = null
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> if (UnlockSupport.isUnlocked) {
                        lockJob = scope.launch {
                            delay(AUTO_LOCK_MILLIS)
                            UnlockSupport.lock()
                            if (destination == AppDestination.Home) destination = AppDestination.Unlock
                        }
                    }
                    Lifecycle.Event.ON_START -> lockJob?.cancel()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                lockJob?.cancel()
            }
        }

        // Expose every screen's testTag as an Android resource-id so Maestro `id:` can query the
        // unlock/home E2E flows (Android-only; no-op iOS) — mirrors OnboardingRoot (KAN-103 test-fix).
        Box(modifier = Modifier.fillMaxSize().enableTestTagsAsResourceId()) {
            when (destination) {
            AppDestination.Resolving -> Centered { ProgressRing(diameter = 48.dp) }
            AppDestination.Onboarding -> OnboardingRoot(onComplete = { destination = AppDestination.Home })
            AppDestination.Unlock -> UnlockScreen(
                onUnlocked = { destination = AppDestination.Home },
                // "Forgot password?" → non-custodial recovery via the import flow (SPEC_UNLOCK):
                // route back through onboarding (import path), which replaces the on-device wallet.
                onRecover = { destination = AppDestination.Onboarding },
            )
            AppDestination.Home -> WalletShellRoot(
                // Lost/expired seed session → clear + back to unlock.
                onLock = {
                    UnlockSupport.lock()
                    destination = AppDestination.Unlock
                },
            )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(CryptasaTheme.colors.surface).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
