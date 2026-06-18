package com.tneff.cyppie.send

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.evm.abi.Erc20Abi
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.FeeData
import com.tneff.cyppie.rpc.RpcException
import com.tneff.cyppie.rpc.TransactionReceipt
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.tx.EvmTransactionSigner
import com.tneff.cyppie.walletcore.EvmChain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeRpc(
    val nonce: Quantity = Quantity.of(7),
    val fee: FeeData = FeeData(Quantity.of(100), Quantity.of(2), Quantity.of(202)),
    val gas: Quantity = Quantity.of(21_000),
    val balance: Quantity = Quantity.of(10_000_000_000_000_000L),
    val broadcastError: RpcException? = null,
    override val degraded: Boolean = false,
) : EvmRpcClient {
    var lastRawTx: String? = null
    override suspend fun getTransactionCount(address: EvmAddress, pending: Boolean) = nonce
    override suspend fun getFeeData() = fee
    override suspend fun estimateGas(from: EvmAddress, to: EvmAddress, value: Quantity, data: ByteArray) = gas
    override suspend fun getBalance(address: EvmAddress) = balance
    override suspend fun sendRawTransaction(rawTransactionHex: String): String {
        lastRawTx = rawTransactionHex
        broadcastError?.let { throw it }
        return "0xtxhash"
    }
    override suspend fun getErc20Balance(token: EvmAddress, owner: EvmAddress) = throw NotImplementedError()
    override suspend fun call(to: EvmAddress, data: ByteArray): ByteArray = throw NotImplementedError()
    override suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? = null
    override suspend fun awaitReceipt(txHash: String, pollIntervalMillis: Long, timeoutMillis: Long) = throw NotImplementedError()
}

/** SeedSource that records zeroization so we can assert the minimal seed window (M1). */
private class CloseableSeed(seed: ByteArray) : SeedSource, AutoCloseable {
    private val delegate = SeedSource.ofSeed(seed)
    var closed = false
        private set
    override fun <R> withSeed(block: (ByteArray) -> R): R = delegate.withSeed(block)
    override fun close() { closed = true }
}

class SendOrchestratorTest {

    private val seed = Mnemonic.of("test test test test test test test test test test test junk").toSeed()
    private val account0 = EvmKeyManager(SeedSource.ofSeed(seed)).deriveAccount(0)
    private val recipient = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
    private val token = EvmAddress.parse("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359")

    private fun orch(rpc: FakeRpc) = SendOrchestrator(mapOf(EvmChain.ETHEREUM.chainId to rpc))

    private fun input(
        from: EvmAddress = account0.address,
        to: EvmAddress = recipient,
        value: Quantity = Quantity.of(1_000),
        data: ByteArray = ByteArray(0),
        nonce: Quantity? = null,
        maxFeePerGas: Quantity? = null,
        maxPriorityFeePerGas: Quantity? = null,
    ) = SendInput(from, to, value, data, EvmChain.ETHEREUM, nonce, null, maxFeePerGas, maxPriorityFeePerGas)

    @Test
    fun prepareCompletesAndDiscloses() = runTest {
        val prepared = orch(FakeRpc()).prepare(input(), listOf(account0))
        assertEquals(0, prepared.accountIndex)
        assertEquals(Quantity.of(7), prepared.disclosure.nonce)
        assertEquals(Quantity.of(21_000) * Quantity.of(202), prepared.disclosure.maxNetworkFee)
        assertEquals(DecodedCall.NativeTransfer, prepared.disclosure.call)
        assertEquals(EvmChain.ETHEREUM.chainId, prepared.transaction.chainId) // #4 chain binding
    }

    @Test
    fun fillsMissingButHonoursProvided() = runTest {
        // RPC would return nonce 7; the input pins nonce 5 + fees → those must win (#2).
        val prepared = orch(FakeRpc()).prepare(
            input(nonce = Quantity.of(5), maxFeePerGas = Quantity.of(500), maxPriorityFeePerGas = Quantity.of(1)),
            listOf(account0),
        )
        assertEquals(Quantity.of(5), prepared.disclosure.nonce)
        assertEquals(Quantity.of(500), prepared.disclosure.maxFeePerGas)
        assertEquals(Quantity.of(1), prepared.disclosure.maxPriorityFeePerGas)
    }

    @Test
    fun unknownAccountIsRejected() = runTest {
        assertFailsWith<SendError.AccountMismatch> {
            orch(FakeRpc()).prepare(input(from = recipient), listOf(account0)) // recipient isn't a wallet account
        }
    }

    @Test
    fun unsupportedChainThrows() = runTest {
        assertFailsWith<SendError.UnsupportedChain> {
            SendOrchestrator(emptyMap()).prepare(input(), listOf(account0))
        }
    }

    @Test
    fun insufficientFundsThrows() = runTest {
        assertFailsWith<SendError.InsufficientFunds> {
            orch(FakeRpc(balance = Quantity.of(100))).prepare(input(), listOf(account0))
        }
    }

    @Test
    fun decodesErc20TransferForDisclosure() = runTest {
        val data = Erc20Abi.transfer(recipient, Quantity.of(5_000))
        val prepared = orch(FakeRpc()).prepare(input(to = token, value = Quantity.ZERO, data = data), listOf(account0))
        val call = prepared.disclosure.call
        assertIs<DecodedCall.Erc20Transfer>(call)
        assertEquals(recipient, call.recipient)
        assertEquals(Quantity.of(5_000), call.amount)
    }

    @Test
    fun signAndBroadcastSignsZeroizesThenBroadcasts() = runTest {
        val rpc = FakeRpc()
        val prepared = orch(rpc).prepare(input(), listOf(account0))
        val seedSource = CloseableSeed(seed)
        val result = orch(rpc).signAndBroadcast(prepared, seedSource)
        assertEquals("0xtxhash", result.txHash)
        assertTrue(seedSource.closed, "seed must be zeroized right after signing (M1)")
        assertNotNull(rpc.lastRawTx)
        assertTrue(rpc.lastRawTx!!.startsWith("0x02")) // EIP-1559 typed tx
    }

    @Test
    fun signedTxEqualsDisclosureAndRecoversToBoundAccount() = runTest {
        // Guardrail #1 (signed == disclosed) + #3/#4 (the signature is over the disclosed, chain-bound
        // tx and recovers to exactly the bound account — no swap between disclosure and signature).
        val prepared = orch(FakeRpc()).prepare(input(), listOf(account0))
        val tx = prepared.transaction
        val d = prepared.disclosure
        assertEquals(d.nonce, tx.nonce)
        assertEquals(d.gasLimit, tx.gasLimit)
        assertEquals(d.maxFeePerGas, tx.maxFeePerGas)
        assertEquals(d.maxPriorityFeePerGas, tx.maxPriorityFeePerGas)
        assertEquals(d.value, tx.value)
        assertEquals(EvmChain.ETHEREUM.chainId, tx.chainId)

        val signer = EvmTransactionSigner(EvmKeyManager(SeedSource.ofSeed(seed)))
        val signed = signer.sign(tx, prepared.accountIndex)
        assertEquals(account0.address, signer.recoverSigner(tx, signed))
    }

    @Test
    fun nodeRejectionOnBroadcastIsTransactionRejected() = runTest {
        val rpc = FakeRpc(broadcastError = RpcException.Node(-32000, "nonce too low"))
        val prepared = orch(rpc).prepare(input(), listOf(account0))
        val seedSource = CloseableSeed(seed)
        assertFailsWith<SendError.TransactionRejected> { orch(rpc).signAndBroadcast(prepared, seedSource) }
        assertTrue(seedSource.closed, "seed zeroized even when broadcast fails")
    }
}
