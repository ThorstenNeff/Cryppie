package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KAN-122 — the pure chain-id derivation behind [WalletConnectController.approvedChains] (#4 chain-binding).
 * The platform `actual`s only fetch the raw namespace lists from the SDK session store; this logic — which
 * decides exactly which chains a session approved — is common and unit-tested here.
 */
class WcApprovedChainsTest {

    @Test
    fun parsesCaip2AndBareAndPrefixedIds() {
        assertEquals(1L, caip2ChainIdOrNull("eip155:1"))
        assertEquals(8453L, caip2ChainIdOrNull("eip155:8453"))
        assertEquals(137L, caip2ChainIdOrNull("137")) // bare numeric id
        assertEquals(10L, caip2ChainIdOrNull("EIP155:10")) // case-insensitive namespace
    }

    @Test
    fun rejectsNonEvmAndUnparseableRefs() {
        assertNull(caip2ChainIdOrNull("cosmos:cosmoshub-4")) // non-eip155 namespace
        assertNull(caip2ChainIdOrNull("solana:5eykt4Usef")) // non-eip155 namespace
        assertNull(caip2ChainIdOrNull("eip155:mainnet")) // non-numeric reference
        assertNull(caip2ChainIdOrNull("eip155:1:0xabc")) // CAIP-10 is not a chain ref
    }

    @Test
    fun derivesFromNamespaceChains() {
        assertEquals(
            setOf(1L, 8453L, 137L),
            approvedChainIdsFrom(chains = listOf("eip155:1", "eip155:8453", "eip155:137"), accounts = emptyList()),
        )
    }

    @Test
    fun derivesFromAccountPrefixesWhenChainsAbsent() {
        // A namespace may omit `chains`; accounts (CAIP-10) always carry the chain ref — robust fallback.
        assertEquals(
            setOf(1L, 137L),
            approvedChainIdsFrom(
                chains = emptyList(),
                accounts = listOf("eip155:1:0xabc0000000000000000000000000000000000001", "eip155:137:0xabc0000000000000000000000000000000000001"),
            ),
        )
    }

    @Test
    fun unionsChainsAndAccountsAndDedups() {
        // chains and accounts overlap (chain 1) and complement (8453 only via accounts) → union, no dupes.
        assertEquals(
            setOf(1L, 8453L),
            approvedChainIdsFrom(
                chains = listOf("eip155:1"),
                accounts = listOf("eip155:1:0xabc0000000000000000000000000000000000001", "eip155:8453:0xabc0000000000000000000000000000000000001"),
            ),
        )
    }

    @Test
    fun dropsNonEvmChainsFromAMixedSession() {
        // A multi-namespace session (eip155 + cosmos) yields only its EVM chain ids.
        assertEquals(
            setOf(1L),
            approvedChainIdsFrom(
                chains = listOf("eip155:1", "cosmos:cosmoshub-4"),
                accounts = listOf("cosmos:cosmoshub-4:cosmos1abc"),
            ),
        )
    }

    @Test
    fun emptyWhenNoChainsOrAccounts() {
        assertTrue(approvedChainIdsFrom(chains = emptyList(), accounts = emptyList()).isEmpty())
    }

    // --- approvedAccounts (#3 / M3 account binding) ---

    private val addr1 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val addr2 = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"

    @Test
    fun extractsEvmAddressFromCaip10() {
        assertEquals(EvmAddress.parse(addr1), caip10AddressOrNull("eip155:1:$addr1"))
        assertEquals(EvmAddress.parse(addr2), caip10AddressOrNull("eip155:11155111:$addr2")) // testnet too
    }

    @Test
    fun rejectsNonEvmOrMalformedCaip10() {
        assertNull(caip10AddressOrNull("cosmos:cosmoshub-4:cosmos1abc")) // non-eip155
        assertNull(caip10AddressOrNull("eip155:1")) // CAIP-2, not an account
        assertNull(caip10AddressOrNull("eip155:1:0xnothex")) // unparseable address
    }

    @Test
    fun derivesApprovedAddressesAcrossChainsAndDedups() {
        // The same address on two chains (CAIP-10) collapses to one EvmAddress; non-EVM dropped.
        assertEquals(
            setOf(EvmAddress.parse(addr1), EvmAddress.parse(addr2)),
            approvedAddressesFrom(
                listOf("eip155:1:$addr1", "eip155:8453:$addr1", "eip155:11155111:$addr2", "cosmos:hub:cosmos1xyz"),
            ),
        )
    }
}
