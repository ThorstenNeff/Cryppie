package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.abi.Erc20Abi
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.FeeData
import com.tneff.cyppie.rpc.RpcException
import com.tneff.cyppie.rpc.TransactionReceipt
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** RPC fake answering `eth_call` by selector; everything else is unused by token resolution. */
private class TokenRpc(private val onCall: (selector: ByteArray) -> ByteArray) : EvmRpcClient {
    override val degraded = false
    override suspend fun call(to: EvmAddress, data: ByteArray): ByteArray = onCall(data.copyOfRange(0, 4))
    override suspend fun getBalance(address: EvmAddress) = throw NotImplementedError()
    override suspend fun getErc20Balance(token: EvmAddress, owner: EvmAddress) = throw NotImplementedError()
    override suspend fun getTransactionCount(address: EvmAddress, pending: Boolean) = throw NotImplementedError()
    override suspend fun getFeeData(): FeeData = throw NotImplementedError()
    override suspend fun sendRawTransaction(rawTransactionHex: String): String = throw NotImplementedError()
    override suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? = null
    override suspend fun awaitReceipt(txHash: String, pollIntervalMillis: Long, timeoutMillis: Long): TransactionReceipt =
        throw NotImplementedError()
}

class TokenResolverTest {

    private val accountManager = AccountManager(
        EvmKeyManager(SeedSource.ofSeed(Mnemonic.of("test test test test test test test test test test test junk").toSeed())),
    )
    private val token = EvmAddress.parse("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359")

    private fun repo(rpc: EvmRpcClient) = WalletRepository(accountManager, mapOf(EvmChain.ETHEREUM.chainId to rpc))

    private fun bytes32(s: String) = ByteArray(32).also { s.encodeToByteArray().copyInto(it) }
    private fun uint256(n: Int) = ByteArray(32).also { it[31] = n.toByte() }

    @Test
    fun resolvesSymbolAndDecimals() = runTest {
        val rpc = TokenRpc { selector ->
            when {
                selector.contentEquals(Erc20Abi.SYMBOL_SELECTOR) -> bytes32("USDC")
                selector.contentEquals(Erc20Abi.DECIMALS_SELECTOR) -> uint256(6)
                else -> throw NotImplementedError()
            }
        }
        val result = repo(rpc).resolveErc20(token, EvmChain.ETHEREUM)
        assertIs<TokenResolution.Resolved>(result)
        assertEquals("USDC", result.token.symbol)
        assertEquals(6, result.token.decimals)
        assertEquals(token, result.token.address)
    }

    @Test
    fun revertIsNotErc20() = runTest {
        val rpc = TokenRpc { throw RpcException.Node(3, "execution reverted") }
        assertEquals(TokenResolution.NotErc20, repo(rpc).resolveErc20(token, EvmChain.ETHEREUM))
    }

    @Test
    fun emptyResultIsNotErc20() = runTest {
        val rpc = TokenRpc { ByteArray(0) } // EOA: eth_call returns 0x → undecodable
        assertEquals(TokenResolution.NotErc20, repo(rpc).resolveErc20(token, EvmChain.ETHEREUM))
    }

    @Test
    fun transportFailureIsNetworkError() = runTest {
        val rpc = TokenRpc { throw RpcException.AllProvidersFailed("all down") }
        assertEquals(TokenResolution.NetworkError, repo(rpc).resolveErc20(token, EvmChain.ETHEREUM))
    }

    @Test
    fun noProviderForChainIsNetworkError() = runTest {
        val result = WalletRepository(accountManager, emptyMap()).resolveErc20(token, EvmChain.BASE)
        assertEquals(TokenResolution.NetworkError, result)
    }
}
