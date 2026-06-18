package com.tneff.cyppie

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tneff.cyppie.di.appModules
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

/**
 * Shared entry composable rendered by every platform target. Bootstraps Koin once for the whole app
 * (ADR-0007) and renders the launch-routing app shell ([AppRoot]: onboarding → unlock → home, KAN-89).
 */
@Composable
@Preview
fun App() {
    KoinApplication(koinConfiguration { modules(appModules) }) {
        AppRoot()
    }
}
