package com.tneff.cyppie.send

import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.RpcException
import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.tx.Eip1559Transaction
import com.tneff.cyppie.wallet.tx.EvmTransactionSigner

/**
 * The one Send orchestrator (KAN-91 / ADR-0019 pending) for **both** entry points (in-app send and
 * WalletConnect `eth_sendTransaction`). Enforces the security guardrails:
 * - **#1 TOCTOU / signed == disclosed:** complete → validate → disclose happen in [prepare] (before
 *   approval); [signAndBroadcast] signs exactly that tx — no fee/gas is filled after disclosure.
 * - **#2 fill-not-override:** [prepare] completes only the fields the input left null.
 * - **#3 account binding:** `from` must be a known wallet account; we sign with its derivation index,
 *   resolved from the cached public [EvmAccount]s — no seed needed pre-approval.
 * - **#4 chain binding:** the tx is built on the request's chain id and signed as-is (no chain swap).
 * - **#5 fail-closed:** decoding/validation failures throw [SendError]; nothing partial is signed.
 *
 * Consumes `:rpc` (L3) + `:wallet` (L2 sign). The WalletConnect adapter (decode → [SendInput],
 * respondSessionRequest) lands when `:walletconnect` merges; this module stays free of it.
 */
class SendOrchestrator(
    private val rpcByChain: Map<Long, EvmRpcClient>,
) {

    /** Steps 1–4: resolve account, complete (nonce/fees/gas), validate balance, assemble disclosure. */
    suspend fun prepare(input: SendInput, accounts: List<EvmAccount>): PreparedSend {
        val client = rpcByChain[input.chain.chainId] ?: throw SendError.UnsupportedChain(input.chain)
        // #3 — `from` must be a wallet account; sign with its BIP-44 index (resolved from public addresses).
        val account = accounts.firstOrNull { it.address == input.from }
            ?: throw SendError.AccountMismatch(input.from)

        // #2 — fill only what's missing; a provided value is never overridden.
        val nonce = input.nonce ?: rpcOrThrow { client.getTransactionCount(input.from, pending = true) }
        val feeData = if (input.maxFeePerGas == null || input.maxPriorityFeePerGas == null) {
            rpcOrThrow { client.getFeeData() }
        } else {
            null
        }
        val maxFee = input.maxFeePerGas ?: feeData!!.maxFeePerGas
        val maxPriority = input.maxPriorityFeePerGas ?: feeData!!.maxPriorityFeePerGas
        val gasLimit = input.gasLimit
            ?: rpcOrThrow { client.estimateGas(input.from, input.to, input.value, input.data) }

        // #4 — chain bound to the request's id; this is the tx that gets signed.
        val tx = Eip1559Transaction(
            chainId = input.chain.chainId,
            nonce = nonce,
            maxPriorityFeePerGas = maxPriority,
            maxFeePerGas = maxFee,
            gasLimit = gasLimit,
            to = input.to,
            value = input.value,
            data = input.data,
        )

        // Validate (pre-sign): balance ≥ value + worst-case network fee.
        val maxNetworkFee = gasLimit * maxFee
        val required = input.value + maxNetworkFee
        val balance = rpcOrThrow { client.getBalance(input.from) }
        if (balance < required) throw SendError.InsufficientFunds(balance, required)

        val disclosure = SendDisclosure(
            from = input.from,
            to = input.to,
            chain = input.chain,
            value = input.value,
            nonce = nonce,
            gasLimit = gasLimit,
            maxFeePerGas = maxFee,
            maxPriorityFeePerGas = maxPriority,
            maxNetworkFee = maxNetworkFee,
            call = SendCallDecoder.decode(input.data),
        )
        return PreparedSend(tx, account.index, disclosure)
    }

    /**
     * Steps 6–7: sign the prepared tx over the unlocked [seedSource], **zeroizing it immediately after**
     * (M1, minimal seed-in-memory window), then broadcast. Call only after the user approved the
     * [PreparedSend.disclosure]. Receipt tracking is `EvmRpcClient.awaitReceipt(txHash)` (caller-driven).
     */
    suspend fun signAndBroadcast(prepared: PreparedSend, seedSource: SeedSource): SendResult {
        val signed = try {
            EvmTransactionSigner(EvmKeyManager(seedSource)).sign(prepared.transaction, prepared.accountIndex)
        } finally {
            (seedSource as? AutoCloseable)?.close()
        }
        val client = rpcByChain[prepared.transaction.chainId]
            ?: throw SendError.NetworkError("No RPC for chain ${prepared.transaction.chainId}")
        return try {
            SendResult(client.sendRawTransaction(signed.rawTransactionHex))
        } catch (e: RpcException.Node) {
            throw SendError.TransactionRejected(e.message ?: "Transaction rejected") // authoritative — no retry
        } catch (e: RpcException) {
            throw SendError.NetworkError(e.message ?: "Broadcast failed", e)
        }
    }

    private suspend fun <T> rpcOrThrow(block: suspend () -> T): T = try {
        block()
    } catch (e: RpcException.Node) {
        throw SendError.TransactionRejected(e.message ?: "Transaction would fail") // e.g. estimateGas revert
    } catch (e: RpcException) {
        throw SendError.NetworkError(e.message ?: "Network error", e)
    }
}
