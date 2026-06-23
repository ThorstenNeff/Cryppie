package com.tneff.cyppie.evm.network

import com.tneff.cyppie.evm.Eip7702Authorization
import com.tneff.cyppie.evm.Erc4337UserOp
import com.tneff.cyppie.evm.SmartSessionEnableDigest
import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkEnvironmentTest {

    @Test
    fun mainnet_isByteIdenticalToThePinnedConstants() {
        val m = NetworkEnvironment.MAINNET.profile
        assertEquals(listOf(1L, 8453L), m.chainIds)
        assertEquals(setOf("eth-mainnet", "base-mainnet"), m.alchemySlugs)
        assertEquals("eth-mainnet", m.alchemySlug(1L))
        assertEquals("base-mainnet", m.alchemySlug(8453L))
        // per-chain UniversalRouter — the exact pre-ADR values
        assertEquals("0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af", m.universalRouter(1L))
        assertEquals("0x6fF5693b99212Da76ad316178A184AB56D299b43", m.universalRouter(8453L))
        // CREATE2-uniform AA pins == the existing :evm constants
        assertEquals(Erc4337UserOp.ENTRY_POINT_V07, m.aa.entryPoint)
        assertEquals(Eip7702Authorization.KERNEL_V3_3_IMPLEMENTATION, m.aa.kernelV3Implementation)
        assertEquals(SmartSessionEnableDigest.SMART_SESSION_ADDRESS, m.aa.smartSession)
        assertEquals(SmartSessionGrantVerifier.SESSION_VALIDATOR, m.aa.ownableValidator)
        assertEquals(SmartSessionGrantVerifier.SPENDING_LIMIT_POLICY, m.aa.spendingLimits)
        assertEquals(SmartSessionGrantVerifier.TIMEFRAME_POLICY, m.aa.timeFrame)
        assertEquals(SmartSessionGrantVerifier.PERMIT2, m.aa.permit2)
    }

    @Test
    fun verifierAndDigestGates_readFromTheRegistry_mainnetUnchanged() {
        // The consolidated consumers must still return the pinned mainnet values (regression guard).
        assertEquals("0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af", SmartSessionGrantVerifier.universalRouter(1L))
        assertEquals("0x6fF5693b99212Da76ad316178A184AB56D299b43", SmartSessionGrantVerifier.universalRouter(8453L))
        assertNull(SmartSessionGrantVerifier.universalRouter(137L)) // unsupported → null (Polygon)
        assertTrue(1L in SmartSessionEnableDigest.SUPPORTED_CHAIN_IDS)
        assertTrue(8453L in SmartSessionEnableDigest.SUPPORTED_CHAIN_IDS)
    }

    @Test
    fun testnet_chainsAndSlugs_areBehindTheEnv() {
        val t = NetworkEnvironment.TESTNET.profile
        assertEquals(listOf(84532L, 11155111L), t.chainIds)
        assertEquals(setOf("base-sepolia", "eth-sepolia"), t.alchemySlugs)
        assertTrue(t.isTestnet)
        // V4 UniversalRouter — Context7-verified vs official Uniswap V4 deployments (Copy/Strategy).
        assertEquals("0x492e6456d9528771018deb9e87ef7750ef184104", t.universalRouter(84532L))
        assertEquals("0x3A9D48AB9751398BbFa63ad67599Bb04e4BdF98b", t.universalRouter(11155111L))
        // dcaRouter (V3 SwapRouter02) stays unfilled (flagged) → DCA fail-closed on testnet until sourced.
        assertNull(t.chain(84532L)?.dcaRouter)
        // CREATE2-uniform AA addresses are the same value on testnet
        assertEquals(NetworkEnvironment.MAINNET.profile.aa, t.aa)
        // testnet chain ids join the hard-gate union
        assertTrue(84532L in SmartSessionEnableDigest.SUPPORTED_CHAIN_IDS)
        assertTrue(11155111L in SmartSessionEnableDigest.SUPPORTED_CHAIN_IDS)
    }

    @Test
    fun chainProfile_resolvesAcrossEnvs_disjoint() {
        assertEquals(1L, NetworkProfiles.chainProfile(1L)?.chainId)
        assertEquals(84532L, NetworkProfiles.chainProfile(84532L)?.chainId)
        assertNull(NetworkProfiles.chainProfile(137L))
    }
}
