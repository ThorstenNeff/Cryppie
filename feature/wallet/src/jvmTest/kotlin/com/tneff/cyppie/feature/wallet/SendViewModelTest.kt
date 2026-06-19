package com.tneff.cyppie.feature.wallet

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.rpc.EvmRpcClient
import com.tneff.cyppie.rpc.FeeData
import com.tneff.cyppie.rpc.ReceiptStatus
import com.tneff.cyppie.rpc.TransactionReceipt
import com.tneff.cyppie.send.DecodedCall
import com.tneff.cyppie.send.SendOrchestrator
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.Mnemonic
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletcore.AccountManager
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * KAN-110 — [SendViewModel] flow over a fake RPC (real orchestrator + on-device signing on JVM).
 * `viewModelScope` runs on an [UnconfinedTestDispatcher] Main, so prepare/sign settle synchronously.
 */
class SendViewModelTest {

    private val seed = Mnemonic.of("test test test test test test test test test test test junk").toSeed()
    private val account0 = EvmKeyManager(SeedSource.ofSeed(seed)).deriveAccount(0)
    private val recipient = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeRpc(
        val nonce: Quantity = Quantity.of(7),
        val fee: FeeData = FeeData(Quantity.of(1_000_000_000), Quantity.of(2_000_000_000), Quantity.of(4_000_000_000)),
        val gas: Quantity = Quantity.of(21_000),
        val balance: Quantity = Quantity.of(10_000_000_000_000_000L), // 0.01 ETH
        override val degraded: Boolean = false,
    ) : EvmRpcClient {
        var lastRawTx: String? = null
        override suspend fun getTransactionCount(address: EvmAddress, pending: Boolean) = nonce
        override suspend fun getFeeData() = fee
        override suspend fun estimateGas(from: EvmAddress, to: EvmAddress, value: Quantity, data: ByteArray) = gas
        override suspend fun getBalance(address: EvmAddress) = balance
        override suspend fun getErc20Balance(token: EvmAddress, owner: EvmAddress) = Quantity.of(5_000_000) // 5 USDC (6dp)
        override suspend fun call(to: EvmAddress, data: ByteArray): ByteArray = ByteArray(0)
        override suspend fun sendRawTransaction(rawTransactionHex: String): String { lastRawTx = rawTransactionHex; return "0xtxhash" }
        override suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? = null
        override suspend fun awaitReceipt(txHash: String, pollIntervalMillis: Long, timeoutMillis: Long): TransactionReceipt =
            throw NotImplementedError()
    }

    /** SeedSource that records zeroization, to assert the per-sign M1 window (close after signing). */
    private class CloseableSeed(seed: ByteArray) : SeedSource, AutoCloseable {
        private val delegate = SeedSource.ofSeed(seed)
        var closed = false; private set
        override fun <R> withSeed(block: (ByteArray) -> R): R = delegate.withSeed(block)
        override fun close() { closed = true }
    }

    private fun vm(
        rpc: FakeRpc,
        receipt: ReceiptStatus = ReceiptStatus.SUCCESS,
        reauth: suspend (CharArray) -> SeedSource? = { SeedSource.ofSeed(seed) },
    ): SendViewModel {
        val byChain = mapOf(EvmChain.ETHEREUM.chainId to rpc as EvmRpcClient)
        val repo = WalletRepository(AccountManager(EvmKeyManager(SeedSource.ofSeed(seed))), byChain)
        return SendViewModel(
            repository = repo,
            orchestrator = SendOrchestrator(byChain),
            feeData = { rpc.getFeeData() },
            awaitReceipt = { _, _ -> receipt },
            reauth = reauth,
            accounts = listOf(account0),
            accountIndex = 0,
        )
    }

    private fun SendViewModel.nativeEth() = assets.first { it.chain == EvmChain.ETHEREUM && it.token == null }

    /** Drive Confirm → re-auth gate → sign (the only sign path). */
    private fun SendViewModel.authAndSign(password: String = "pw") {
        requestAuth()
        updateAuthPassword(password)
        authorizeAndSign()
    }

    @Test
    fun nativeHappyPathDisclosesAndBroadcasts() = runTest {
        val rpc = FakeRpc()
        val m = vm(rpc)
        m.selectAsset(m.nativeEth())
        assertEquals(SendStep.Form, m.step)
        assertEquals(rpc.balance, m.available)
        m.updateRecipient(recipient)
        m.updateAmount("0.001")
        assertTrue(m.canContinue)
        m.continueToConfirm()
        assertEquals(SendStep.Confirm, m.step)
        val d = m.prepared!!.disclosure
        assertEquals(Quantity.of(1_000_000_000_000_000L), d.value) // 0.001 ETH
        assertEquals(Quantity.of(7), d.nonce)
        assertEquals(Quantity.of(21_000), d.gasLimit)
        assertIs<DecodedCall.NativeTransfer>(d.call)

        m.requestAuth()
        assertEquals(SendStep.Authorize, m.step) // fail-closed: a gate sits between disclosure and sign
        m.updateAuthPassword("pw")
        m.authorizeAndSign()
        assertEquals(SendStatus.Confirmed("0xtxhash"), m.status)
        assertTrue(rpc.lastRawTx != null && rpc.lastRawTx!!.startsWith("0x")) // signed + broadcast
    }

    @Test
    fun freshSourceIsZeroizedAfterSend() = runTest {
        // M1: the per-sign source is closed/zeroized right after signing; nothing else holds it.
        val fresh = CloseableSeed(seed)
        val m = vm(FakeRpc(), reauth = { fresh })
        m.selectAsset(m.nativeEth())
        m.updateRecipient(recipient)
        m.updateAmount("0.001")
        m.continueToConfirm()
        m.authAndSign()
        assertIs<SendStatus.Confirmed>(m.status)
        assertTrue(fresh.closed) // signAndBroadcast zeroized the fresh source
    }

    @Test
    fun wrongPasswordNeverSigns() = runTest {
        // Fail-closed: a wrong password (reauth → null) stays on Authorize and never broadcasts.
        val rpc = FakeRpc()
        val m = vm(rpc, reauth = { null })
        m.selectAsset(m.nativeEth())
        m.updateRecipient(recipient)
        m.updateAmount("0.001")
        m.continueToConfirm()
        m.authAndSign("wrong")
        assertTrue(m.authError)
        assertEquals(SendStep.Authorize, m.step)
        assertEquals(null, m.status)
        assertEquals(null, rpc.lastRawTx) // never signed/broadcast
    }

    @Test
    fun erc20BuildsTransferCalldataAndDisclosesRecipient() = runTest {
        val m = vm(FakeRpc())
        val usdc = m.assets.first { it.chain == EvmChain.ETHEREUM && it.token != null }
        m.selectAsset(usdc)
        m.updateRecipient(recipient)
        m.updateAmount("1") // 1 token unit
        m.continueToConfirm()
        val d = m.prepared!!.disclosure
        assertEquals(usdc.token, d.to)                 // tx `to` is the token contract
        assertEquals(Quantity.ZERO, d.value)           // native value 0 for an ERC-20 transfer
        val call = assertIs<DecodedCall.Erc20Transfer>(d.call)
        assertEquals(EvmAddress.parse(recipient), call.recipient)
        assertEquals(EvmAddress.parse(recipient), m.disclosedRecipient(m.prepared!!)) // UI shows the real recipient
    }

    @Test
    fun amountOverBalanceBlocksContinue() = runTest {
        val m = vm(FakeRpc())
        m.selectAsset(m.nativeEth())
        m.updateRecipient(recipient)
        m.updateAmount("1") // 1 ETH ≫ 0.01 balance
        assertFalse(m.canContinue)
    }

    @Test
    fun insufficientForFeeSurfacesOnPrepare() = runTest {
        val m = vm(FakeRpc())
        m.selectAsset(m.nativeEth())
        m.updateRecipient(recipient)
        m.updateAmount("0.01") // == balance, so balance can't also cover the fee
        assertTrue(m.canContinue) // amount alone is within balance
        m.continueToConfirm()
        assertEquals(SendStep.Form, m.step) // stayed on form
        assertIs<SendFormError.Insufficient>(m.formError)
    }

    @Test
    fun invalidRecipientBlocksContinue() = runTest {
        val m = vm(FakeRpc())
        m.selectAsset(m.nativeEth())
        m.updateAmount("0.001")
        m.updateRecipient("0xdef") // too short / invalid
        assertFalse(m.recipientValid)
        assertFalse(m.canContinue)
    }

    @Test
    fun nodeRejectBecomesRejectedStatus() = runTest {
        // estimateGas/broadcast revert → TransactionRejected → Failed(rejected=true). Simulate via a
        // broadcast that throws RpcException.Node by using a fee that passes prepare, then a rejecting rpc.
        val rpc = object : EvmRpcClient by FakeRpc() {
            override suspend fun sendRawTransaction(rawTransactionHex: String): String =
                throw com.tneff.cyppie.rpc.RpcException.Node(-32000, "execution reverted")
        }
        val byChain = mapOf(EvmChain.ETHEREUM.chainId to rpc)
        val m = SendViewModel(
            repository = WalletRepository(AccountManager(EvmKeyManager(SeedSource.ofSeed(seed))), byChain),
            orchestrator = SendOrchestrator(byChain),
            feeData = { rpc.getFeeData() },
            awaitReceipt = { _, _ -> ReceiptStatus.SUCCESS },
            reauth = { SeedSource.ofSeed(seed) },
            accounts = listOf(account0),
            accountIndex = 0,
        )
        m.selectAsset(m.nativeEth())
        m.updateRecipient(recipient)
        m.updateAmount("0.001")
        m.continueToConfirm()
        assertEquals(SendStep.Confirm, m.step)
        m.authAndSign()
        val failed = assertIs<SendStatus.Failed>(m.status)
        assertTrue(failed.rejected)
    }
}
