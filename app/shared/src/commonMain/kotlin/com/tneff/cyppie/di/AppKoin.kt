package com.tneff.cyppie.di

import com.tneff.cyppie.feature.onboarding.di.onboardingModule
import org.koin.core.module.Module

/**
 * All Koin modules of the app (ADR-0007). The app is the composition root: each feature contributes
 * its own module and they are aggregated here, then provided to the composition by `App()` via
 * `KoinApplication`. Add new feature modules to this list as features land.
 */
val appModules: List<Module> = listOf(
    onboardingModule,
)
