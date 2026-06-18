package com.tneff.cyppie.feature.onboarding.di

import com.tneff.cyppie.feature.onboarding.OnboardingViewModel
import com.tneff.cyppie.feature.onboarding.UnlockViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module of the onboarding feature (ADR-0007). Each feature owns its module; the app aggregates
 * them in `appModules` (`:app:shared`) and provides them to the composition via
 * `KoinApplication(koinConfiguration { modules(appModules) })` in `App()`.
 */
val onboardingModule: Module = module {
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::UnlockViewModel)
}
