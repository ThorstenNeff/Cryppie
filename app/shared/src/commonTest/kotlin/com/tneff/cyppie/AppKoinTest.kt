package com.tneff.cyppie

import com.tneff.cyppie.di.appModules
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * DI smoke test (ADR-0007): the aggregated Koin graph must load without duplicate/missing-definition
 * errors. Uses an isolated [koinApplication] instance so it does not touch any global Koin state.
 */
class AppKoinTest {

    @Test
    fun appModules_areRegistered() {
        assertTrue(appModules.isNotEmpty(), "appModules must aggregate at least the onboarding module")
    }

    @Test
    fun appModules_loadWithoutDefinitionErrors() {
        // Loading the modules fails fast on duplicate/invalid definitions; reaching close() means OK.
        var loaded = false
        val app = koinApplication { modules(appModules) }
        loaded = true
        app.close()
        assertTrue(loaded, "Koin graph loaded the app modules without errors")
    }
}
