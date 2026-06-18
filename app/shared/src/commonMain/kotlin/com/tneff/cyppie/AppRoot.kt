package com.tneff.cyppie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingRoot

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

        when (destination) {
            AppDestination.Resolving -> Centered { ProgressRing(diameter = 48.dp) }
            AppDestination.Onboarding -> OnboardingRoot(onComplete = { destination = AppDestination.Home })
            AppDestination.Unlock -> UnlockStubScreen(onUnlocked = { destination = AppDestination.Home })
            AppDestination.Home -> HomePlaceholder()
        }
    }
}

/** Stub for the returning-user unlock screen (real password/biometric → `SeedSource` UX = KAN-92). */
@Composable
private fun UnlockStubScreen(onUnlocked: () -> Unit) {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Unlock (stub — KAN-92)", color = CryptasaTheme.colors.onSurface)
            CryptasaButton(text = "Unlock", onClick = onUnlocked)
        }
    }
}

/** Placeholder until the live `WalletRepository` is wired (KAN-89 final / KAN-95). */
@Composable
private fun HomePlaceholder() {
    Centered { Text("Wallet-Home (wiring pending KAN-95/KAN-92)", color = CryptasaTheme.colors.onSurface) }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(CryptasaTheme.colors.surface).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
