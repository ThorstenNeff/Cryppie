package com.tneff.cyppie.walletcore

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.wallet.EvmAccount
import kotlin.coroutines.cancellation.CancellationException

/**
 * Read-side wallet domain (KAN read-domain skeleton): aggregates accounts (L1, [AccountManager]) and
 * on-chain reads (L3, [EvmRpcClient] per chain) — native/ERC-20 balances and nonce. One
 * [EvmRpcClient] per [EvmChain] (`rpcByChain` keyed by `chainId`); the app wires the per-chain
 * clients (with build-config RPC keys). Write/send/seed/WalletConnect paths fold in after the
 * KAN-75 (storage) and KAN-62 (WalletConnect) merges.
 */
class WalletRepository(
    private val accountManager: AccountManager,
    private val rpcByChain: Map<Long, EvmRpcClient>,
) {

    /** The first [count] accounts (addresses). */
    fun accounts(count: Int): List<EvmAccount> = accountManager.accounts(count)

    /** Native (ETH) balance in wei for account [accountIndex] on [chain]. */
    suspend fun nativeBalance(accountIndex: Int, chain: EvmChain): Quantity =
        rpc(chain).getBalance(accountManager.account(accountIndex).address)

    /** ERC-20 [token] balance for account [accountIndex] on [chain]. */
    suspend fun tokenBalance(token: EvmAddress, accountIndex: Int, chain: EvmChain): Quantity =
        rpc(chain).getErc20Balance(token, accountManager.account(accountIndex).address)

    /** Pending nonce for account [accountIndex] on [chain] (for later send orchestration). */
    suspend fun nonce(accountIndex: Int, chain: EvmChain): Quantity =
        rpc(chain).getTransactionCount(accountManager.account(accountIndex).address, pending = true)

    /** Receive data (address + EIP-681 QR payload) for account [accountIndex] on [chain]. */
    fun receiveInfo(accountIndex: Int, chain: EvmChain): ReceiveInfo {
        val address = accountManager.account(accountIndex).address
        return ReceiveInfo(address, chain, Eip681.receiveUri(address, chain))
    }

    /**
     * Native + ERC-20 ([tokens]) balances for account [accountIndex] on [chain] (wallet-home).
     * Per-token resilient (L2): a single failing `balanceOf` is skipped (omitted from the map) so one
     * bad token contract doesn't blank the whole wallet-home; the native balance failing still throws.
     */
    suspend fun chainBalances(accountIndex: Int, chain: EvmChain, tokens: List<EvmAddress> = emptyList()): ChainBalances {
        val address = accountManager.account(accountIndex).address
        val client = rpc(chain)
        val native = client.getBalance(address)
        val tokenBalances = buildMap {
            for (token in tokens) {
                try {
                    put(token, client.getErc20Balance(token, address))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // skip this token — keep the rest of wallet-home populated
                }
            }
        }
        return ChainBalances(chain, address, native, tokenBalances, client.degraded)
    }

    /**
     * Wallet-home aggregation: balances for account [accountIndex] across each chain in
     * [tokensByChain] that has a configured provider. Chains without an `EvmRpcClient` are skipped.
     */
    suspend fun accountPortfolio(
        accountIndex: Int,
        tokensByChain: Map<EvmChain, List<EvmAddress>>,
    ): List<ChainBalances> =
        tokensByChain
            .filterKeys { rpcByChain.containsKey(it.chainId) }
            .map { (chain, tokens) -> chainBalances(accountIndex, chain, tokens) }

    /** True if any configured provider for [chain] is currently degraded (on a fallback). */
    fun isDegraded(chain: EvmChain): Boolean = rpcByChain[chain.chainId]?.degraded ?: false

    private fun rpc(chain: EvmChain): EvmRpcClient =
        rpcByChain[chain.chainId] ?: throw IllegalArgumentException("No RPC configured for ${chain.displayName}")
}
