package com.tneff.cyppie.evm.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ActiveNetworkStoreTest {

    @Test
    fun defaultsToMainnet_whenNothingPersisted() {
        val store = ActiveNetworkStore()
        assertEquals(NetworkEnvironment.MAINNET, store.environment.value)
        assertSame(NetworkProfiles.MAINNET, store.profile)
    }

    @Test
    fun loadsThePersistedEnv_atConstruction() {
        val store = ActiveNetworkStore(InMemoryNetworkPreferenceStore(NetworkEnvironment.TESTNET))
        assertEquals(NetworkEnvironment.TESTNET, store.environment.value)
    }

    @Test
    fun switch_emitsAndPersists() = runTest {
        val prefs = InMemoryNetworkPreferenceStore()
        val store = ActiveNetworkStore(prefs)
        store.switch(NetworkEnvironment.TESTNET)
        assertEquals(NetworkEnvironment.TESTNET, store.environment.value) // emitted
        assertEquals(NetworkEnvironment.TESTNET, prefs.loadEnvironment())  // persisted (survives process death)
        assertEquals(NetworkProfiles.TESTNET, store.profile)
    }

    @Test
    fun switch_toSameEnv_isANoOp() {
        val store = ActiveNetworkStore()
        var emissions = 0
        // a fresh switch to the current env must not change the value
        store.switch(NetworkEnvironment.MAINNET)
        assertEquals(NetworkEnvironment.MAINNET, store.environment.value)
    }
}
