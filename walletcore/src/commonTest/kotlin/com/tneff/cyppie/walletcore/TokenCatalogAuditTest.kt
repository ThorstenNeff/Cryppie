package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.abi.Erc20Abi
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.FeeData
import com.tneff.cyppie.rpc.TransactionReceipt
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * KAN-90 — authoritative TokenCatalog address audit (pre-release gate). EIP-55 only catches *case*
 * typos; this pins each entry's address + symbol + decimals against an independently-sourced fixture
 * (Etherscan / BaseScan, verified 2026-06-18) so a typo to *another valid address*, or a wrong
 * symbol/decimals, fails the build. Also checks the catalog's claims against what `resolveErc20` would
 * read from chain (the fixture standing in for the node).
 */
class TokenCatalogAuditTest {

    private data class Authoritative(val chain: EvmChain, val address: String, val symbol: String, val decimals: Int)

    // Cross-checked against Etherscan / BaseScan (KAN-90). Base USDT intentionally absent (bridged,
    // Tether-disclaimed — see TokenCatalog KDoc).
    private val audited = listOf(
        Authoritative(EvmChain.ETHEREUM, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", "USDC", 6),
        Authoritative(EvmChain.ETHEREUM, "0xdAC17F958D2ee523a2206206994597C13D831ec7", "USDT", 6),
        Authoritative(EvmChain.ETHEREUM, "0x6B175474E89094C44Da98b954EedeAC495271d0F", "DAI", 18),
        Authoritative(EvmChain.ETHEREUM, "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", "WETH", 18),
        Authoritative(EvmChain.BASE, "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", "USDC", 6),
        Authoritative(EvmChain.BASE, "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb", "DAI", 18),
        Authoritative(EvmChain.BASE, "0x4200000000000000000000000000000000000006", "WETH", 18),
    )

    private val accountManager = AccountManager(
        EvmKeyManager(SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed())),
    )

    @Test
    fun everyCatalogEntryMatchesTheAuthoritativeAudit() {
        for (a in audited) {
            val entry = TokenCatalog.find(EvmAddress.parse(a.address), a.chain)
            assertNotNull(entry, "Catalog missing ${a.symbol} on ${a.chain.displayName} (${a.address})")
            assertEquals(a.symbol, entry.symbol, "symbol mismatch at ${a.address}")
            assertEquals(a.decimals, entry.decimals, "decimals mismatch at ${a.address}")
        }
        // No catalog entry outside the audited set — a new token must be audited before it ships.
        assertEquals(audited.size, TokenCatalog.tokens.size, "TokenCatalog has un-audited entries")
    }

    @Test
    fun catalogClaimsMatchWhatResolveReadsOnChain() = runTest {
        // A node reporting the audited symbol/decimals must resolve to the catalog's claimed values.
        for (a in audited) {
            val rpc = SymbolDecimalsRpc(a.symbol, a.decimals)
            val repo = WalletRepository(accountManager, mapOf(a.chain.chainId to rpc))
            val resolved = repo.resolveErc20(EvmAddress.parse(a.address), a.chain)
            assertIs<TokenResolution.Resolved>(resolved)
            val entry = TokenCatalog.find(EvmAddress.parse(a.address), a.chain)!!
            assertEquals(entry.symbol, resolved.token.symbol)
            assertEquals(entry.decimals, resolved.token.decimals)
        }
    }

    @Test
    fun bridgedBaseUsdtIsNotInCatalog() {
        // Documents the deliberate omission (bridged, Tether-disclaimed).
        val bridgedBaseUsdt = EvmAddress.parse("0xfde4C96c8593536E31F229EA8f37b2ADa2699bb2")
        assertNull(TokenCatalog.find(bridgedBaseUsdt, EvmChain.BASE))
        assertEquals(emptyList(), TokenCatalog.forChain(EvmChain.BASE).filter { it.symbol == "USDT" })
    }
}

/** Fake node returning a fixed ERC-20 `symbol`/`decimals` (bytes32 + uint256) per the call selector. */
private class SymbolDecimalsRpc(private val symbol: String, private val decimals: Int) : EvmRpcClient {
    override val degraded = false
    override suspend fun call(to: EvmAddress, data: ByteArray): ByteArray = when {
        data.copyOfRange(0, 4).contentEquals(Erc20Abi.SYMBOL_SELECTOR) ->
            ByteArray(32).also { symbol.encodeToByteArray().copyInto(it) }
        data.copyOfRange(0, 4).contentEquals(Erc20Abi.DECIMALS_SELECTOR) ->
            ByteArray(32).also { it[31] = decimals.toByte() }
        else -> throw NotImplementedError()
    }
    override suspend fun getBalance(address: EvmAddress) = throw NotImplementedError()
    override suspend fun getErc20Balance(token: EvmAddress, owner: EvmAddress) = throw NotImplementedError()
    override suspend fun getTransactionCount(address: EvmAddress, pending: Boolean) = throw NotImplementedError()
    override suspend fun getFeeData(): FeeData = throw NotImplementedError()
    override suspend fun estimateGas(from: EvmAddress, to: EvmAddress, value: com.tneff.cyppie.evm.Quantity, data: ByteArray) = throw NotImplementedError()
    override suspend fun sendRawTransaction(rawTransactionHex: String): String = throw NotImplementedError()
    override suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? = null
    override suspend fun awaitReceipt(txHash: String, pollIntervalMillis: Long, timeoutMillis: Long) = throw NotImplementedError()
}
