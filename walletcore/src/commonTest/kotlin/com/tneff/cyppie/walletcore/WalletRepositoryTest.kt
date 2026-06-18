package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.FeeData
import com.tneff.cyppie.rpc.TransactionReceipt
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fake L3 client returning fixed reads; write/poll methods are not exercised by the read domain. */
private class FakeRpc(
    private val balance: Quantity = Quantity.ZERO,
    private val erc20: Quantity = Quantity.ZERO,
    private val accountNonce: Quantity = Quantity.ZERO,
    override val degraded: Boolean = false,
    private val failErc20For: Set<EvmAddress> = emptySet(),
) : EvmRpcClient {
    override suspend fun getBalance(address: EvmAddress) = balance
    override suspend fun getErc20Balance(token: EvmAddress, owner: EvmAddress): Quantity {
        if (token in failErc20For) throw RuntimeException("balanceOf reverted for $token")
        return erc20
    }
    override suspend fun getTransactionCount(address: EvmAddress, pending: Boolean) = accountNonce
    override suspend fun getFeeData(): FeeData = throw NotImplementedError()
    override suspend fun estimateGas(from: EvmAddress, to: EvmAddress, value: Quantity, data: ByteArray) = throw NotImplementedError()
    override suspend fun call(to: EvmAddress, data: ByteArray): ByteArray = throw NotImplementedError()
    override suspend fun sendRawTransaction(rawTransactionHex: String): String = throw NotImplementedError()
    override suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? = null
    override suspend fun awaitReceipt(txHash: String, pollIntervalMillis: Long, timeoutMillis: Long): TransactionReceipt =
        throw NotImplementedError()
}

class WalletRepositoryTest {

    private val keyManager = EvmKeyManager(
        SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed()),
    )

    @Test
    fun listsHardhatAccountAddresses() {
        val repo = WalletRepository(AccountManager(keyManager), emptyMap())
        val accounts = repo.accounts(3)
        assertEquals(3, accounts.size)
        assertEquals("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", accounts[0].address.value)
        assertEquals("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", accounts[1].address.value)
        assertEquals("m/44'/60'/0'/0/2", accounts[2].path)
    }

    @Test
    fun fetchesNativeBalanceFromChainRpc() = runTest {
        val repo = WalletRepository(
            AccountManager(keyManager),
            mapOf(EvmChain.ETHEREUM.chainId to FakeRpc(balance = Quantity.ofHex("0x1bc16d674ec80000"))), // 2 ETH
        )
        assertEquals(Quantity.ofHex("0x1bc16d674ec80000"), repo.nativeBalance(0, EvmChain.ETHEREUM))
    }

    @Test
    fun missingChainRpcThrows() = runTest {
        val repo = WalletRepository(AccountManager(keyManager), mapOf(EvmChain.ETHEREUM.chainId to FakeRpc()))
        assertFailsWith<IllegalArgumentException> { repo.nativeBalance(0, EvmChain.BASE) }
    }

    @Test
    fun reportsDegradedPerChain() {
        val repo = WalletRepository(AccountManager(keyManager), mapOf(EvmChain.ETHEREUM.chainId to FakeRpc(degraded = true)))
        assertTrue(repo.isDegraded(EvmChain.ETHEREUM))
        assertFalse(repo.isDegraded(EvmChain.BASE)) // no client configured → not degraded
    }

    @Test
    fun buildsEip681ReceiveInfo() {
        val repo = WalletRepository(AccountManager(keyManager), emptyMap())
        val info = repo.receiveInfo(0, EvmChain.ETHEREUM)
        assertEquals("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", info.address.value)
        assertEquals("ethereum:0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266@1", info.eip681Uri)
    }

    @Test
    fun aggregatesNativeAndTokenBalances() = runTest {
        val token = EvmAddress.parse("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359")
        val repo = WalletRepository(
            AccountManager(keyManager),
            mapOf(EvmChain.ETHEREUM.chainId to FakeRpc(balance = Quantity.of(5), erc20 = Quantity.of(100), degraded = true)),
        )
        val balances = repo.chainBalances(0, EvmChain.ETHEREUM, listOf(token))
        assertEquals(Quantity.of(5), balances.native)
        assertEquals(Quantity.of(100), balances.tokens[token])
        assertTrue(balances.degraded)
    }

    @Test
    fun portfolioSkipsChainsWithoutConfiguredRpc() = runTest {
        // Only Ethereum configured, but both ETH + Base requested → Base is skipped (L1).
        val repo = WalletRepository(AccountManager(keyManager), mapOf(EvmChain.ETHEREUM.chainId to FakeRpc()))
        val portfolio = repo.accountPortfolio(0, mapOf(EvmChain.ETHEREUM to emptyList(), EvmChain.BASE to emptyList()))
        assertEquals(1, portfolio.size)
        assertEquals(EvmChain.ETHEREUM, portfolio.single().chain)
    }

    @Test
    fun chainBalancesIsolatesFailingToken() = runTest {
        val good = EvmAddress.parse("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359")
        val bad = EvmAddress.parse("0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB")
        val repo = WalletRepository(
            AccountManager(keyManager),
            mapOf(EvmChain.ETHEREUM.chainId to FakeRpc(balance = Quantity.of(7), erc20 = Quantity.of(50), failErc20For = setOf(bad))),
        )
        val balances = repo.chainBalances(0, EvmChain.ETHEREUM, listOf(good, bad))
        assertEquals(Quantity.of(7), balances.native) // native unaffected
        assertEquals(Quantity.of(50), balances.tokens[good]) // good token present
        assertFalse(balances.tokens.containsKey(bad)) // failing token skipped, not blanking the rest
    }

    @Test
    fun portfolioCoversConfiguredChainsAndSkipsOthers() = runTest {
        val repo = WalletRepository(
            AccountManager(keyManager),
            mapOf(
                EvmChain.ETHEREUM.chainId to FakeRpc(balance = Quantity.of(1)),
                EvmChain.BASE.chainId to FakeRpc(balance = Quantity.of(2)),
            ),
        )
        // POLYGON-style unconfigured chain isn't possible here (enum is ETH/BASE), so request both +
        // verify both are covered; a chain with no client would be skipped by accountPortfolio.
        val portfolio = repo.accountPortfolio(0, mapOf(EvmChain.ETHEREUM to emptyList(), EvmChain.BASE to emptyList()))
        assertEquals(2, portfolio.size)
        assertEquals(setOf(EvmChain.ETHEREUM, EvmChain.BASE), portfolio.map { it.chain }.toSet())
    }
}
