package com.tneff.cyppie.feature.copy

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val OWNER = EvmAddress.parse("0x" + "b".repeat(40))
private val VALID_TRADER = "0x" + "a".repeat(40) // EIP-55-valid (all-lower = no checksum), ≠ OWNER

private class FakeSeed : SeedSource, AutoCloseable {
    var closed = false
    override fun <R> withSeed(block: (ByteArray) -> R): R = block(ByteArray(32))
    override fun close() { closed = true }
}

/** KAN-155 — the Follow-Trader flow: e2e happy path + EIP-55/self-copy guards + fail-closed verify + no-blind. */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        service: FollowGrantService = StubFollowGrantService { 1_000L },
        reauth: suspend (String) -> SeedSource? = { FakeSeed() },
    ) = FollowViewModel(service, OWNER, budgetTokenDecimals = 6, reauth = reauth)

    @Test
    fun fullFlow_reachesDone_withVerifiedCapEchoingBudget() = runTest {
        val vm = vm()
        vm.enterTrader(VALID_TRADER); vm.toBudget()
        assertEquals(FollowStep.Budget, vm.step)
        vm.enterBudget("100"); vm.review()
        assertEquals(FollowStep.Review, vm.step)
        assertNotNull(vm.prepared)
        assertEquals("100", vm.capHuman(vm.prepared!!.verifiedGrant.capBaseUnits)) // 100 * 10^6 base units → "100"
        assertEquals(VALID_TRADER, vm.prepared!!.source) // followed trader = advisory
        assertEquals(10_000, vm.prepared!!.allocationBps)
        vm.confirm("pw")
        assertEquals(FollowStep.Done, vm.step)
        assertNull(vm.error)
    }

    @Test
    fun invalidAddress_blocksAtScreen1() {
        val vm = vm()
        vm.enterTrader("0xnothex"); vm.toBudget()
        assertEquals(FollowStep.SelectTrader, vm.step)
        assertEquals(CopyError.INVALID_ADDRESS, vm.error)
    }

    @Test
    fun selfCopy_isRejected() {
        val vm = vm()
        vm.enterTrader(OWNER.value); vm.toBudget()
        assertEquals(FollowStep.SelectTrader, vm.step)
        assertEquals(CopyError.SELF_COPY, vm.error)
    }

    @Test
    fun verifyFailure_isFailClosed_noPreparedNoAdvance() = runTest {
        val failing = object : FollowGrantService {
            override suspend fun prepare(trader: String, budgetBaseUnits: String): CopyGrantPreview = throw RuntimeException("mismatch")
            override suspend fun activate(preview: CopyGrantPreview, seedSource: SeedSource) {}
        }
        val vm = vm(service = failing)
        vm.enterTrader(VALID_TRADER); vm.toBudget(); vm.enterBudget("100"); vm.review()
        assertNull(vm.prepared)
        assertEquals(CopyError.VERIFY_FAILED, vm.error)
        assertEquals(FollowStep.Budget, vm.step) // never reaches Review/sign
    }

    @Test
    fun confirmWithoutPrepared_refusesToSign() {
        val vm = vm()
        vm.confirm("pw") // no review → no prepared grant
        assertEquals(CopyError.VERIFY_FAILED, vm.error)
        assertEquals(FollowStep.SelectTrader, vm.step)
    }

    @Test
    fun wrongPassword_staysOnReview() = runTest {
        val vm = vm(reauth = { null }) // re-auth fails
        vm.enterTrader(VALID_TRADER); vm.toBudget(); vm.enterBudget("100"); vm.review()
        vm.confirm("bad")
        assertEquals(CopyError.WRONG_PASSWORD, vm.error)
        assertEquals(FollowStep.Review, vm.step)
    }

    @Test
    fun editingBudget_clearsPriorReview() = runTest {
        val vm = vm()
        vm.enterTrader(VALID_TRADER); vm.toBudget(); vm.enterBudget("100"); vm.review()
        assertNotNull(vm.prepared)
        vm.back() // → Budget
        vm.enterBudget("200")
        assertNull(vm.prepared) // must re-verify
        assertEquals(FollowStep.Budget, vm.step)
    }
}
