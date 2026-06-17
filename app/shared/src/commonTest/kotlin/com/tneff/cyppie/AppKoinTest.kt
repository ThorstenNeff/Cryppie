package com.tneff.cyppie

import com.tneff.cyppie.di.appModules
import com.tneff.cyppie.feature.onboarding.OnboardingViewModel
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * DI smoke test (ADR-0007): the aggregated Koin graph must load without duplicate/missing-definition
 * errors, and the wired definitions must actually resolve. Uses an isolated [koinApplication]
 * instance so it does not touch any global Koin state.
 */
class AppKoinTest {

    @Test
    fun appModules_areRegistered() {
        assertTrue(appModules.isNotEmpty(), "appModules must aggregate at least the onboarding module")
    }

    @Test
    fun onboardingViewModel_resolvesFromGraph() {
        val app = koinApplication { modules(appModules) }
        try {
            val viewModel = app.koin.get<OnboardingViewModel>()
            assertNotNull(viewModel, "OnboardingViewModel must resolve from the Koin graph")
        } finally {
            app.close()
        }
    }
}
