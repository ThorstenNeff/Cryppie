package com.tneff.cyppie.feature.copy

import com.tneff.cyppie.aa.BuiltCopyEnable
import com.tneff.cyppie.aa.CopyGrantPreview
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import com.tneff.cyppie.evm.VerifiedGrant
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val OWNER = EvmAddress.parse("0x" + "b".repeat(40))
private val VALID_TRADER = "0x" + "a".repeat(40) // EIP-55-valid (all-lower = no checksum), ≠ OWNER
private val VALID_TOKEN = "0x" + "c".repeat(40)   // fixed-mode receive token
private val USDC = "0x" + "d".repeat(40)
private val ROUTER = "0x" + "e".repeat(40)
private const val SELECTOR = "0x3593564c"

/** A shape-correct stand-in enable; the VM never inspects it (passed opaquely to authorizeGrant). */
private val DUMMY_ENABLE = BuiltCopyEnable(
    digestToSign = "0x", account = OWNER.value, chainId = 1L,
    sessionValidator = "0x", sessionValidatorInitData = "0x", salt = "0x", nonce = "0x0",
    permissionId = "0x", permissions = SignedPermissions(permitERC4337Paymaster = true),
)

private class FakeSeed : SeedSource, AutoCloseable {
    var closed = false
    override fun <R> withSeed(block: (ByteArray) -> R): R = block(ByteArray(32))
    override fun close() { closed = true }
}

/** KAN-155/161 — the Follow-Trader flow: e2e happy path (fixed+dynamic) + guards + fail-closed verify + no-blind. */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // Captured prepareGrant args (assert the mode → tokenOut mapping).
    private var lastTokenOut: String? = null
    private var capturedTokenOut: Boolean = false

    private fun preview(trader: String, cap: String) = CopyGrantPreview(
        verifiedGrant = VerifiedGrant(
            account = OWNER.value, chainId = 1L, actionTarget = ROUTER, actionSelector = SELECTOR,
            spendToken = USDC, capBaseUnits = cap, windowStartEpochSeconds = 1_000L, windowEndEpochSeconds = 1_000L + 90L * 86_400,
        ),
        source = trader, allocationBps = 10_000, enable = DUMMY_ENABLE,
    )

    private fun vm(
        prepareGrant: suspend (String, String, String?) -> CopyGrantPreview = { trader, budget, tokenOut ->
            lastTokenOut = tokenOut; capturedTokenOut = true; preview(trader, budget)
        },
        authorizeGrant: suspend (CopyGrantPreview, SeedSource) -> Unit = { _, seed -> (seed as? AutoCloseable)?.close() },
        reauth: suspend (String) -> SeedSource? = { FakeSeed() },
        allowlistTokens: List<CopyToken> = emptyList(),
    ) = FollowViewModel(owner = OWNER, budgetTokenDecimals = 6, prepareGrant = prepareGrant, authorizeGrant = authorizeGrant, reauth = reauth, allowlistTokens = allowlistTokens)

    @Test
    fun tokenPicker_selectsAllowlistToken_setsTokenOutAndCloses() {
        val tok = CopyToken(VALID_TOKEN, "WETH", "Wrapped Ether")
        val m = vm(allowlistTokens = listOf(tok))
        m.selectMode(CopyMode.FIXED)
        m.openTokenPicker()
        assertTrue(m.tokenPickerOpen)
        m.selectToken(tok)
        assertFalse(m.tokenPickerOpen)
        assertEquals(VALID_TOKEN, m.tokenOut)
        assertEquals(tok, m.selectedToken)
        assertTrue(m.modeReady) // a picked allowlist token is a valid address
    }

    /** Drive the flow to Review in FIXED mode with a valid token + the given budget. */
    private fun FollowViewModel.toReviewFixed(budget: String = "100") {
        enterTrader(VALID_TRADER); toMode()
        selectMode(CopyMode.FIXED); enterTokenOut(VALID_TOKEN); toBudget()
        enterBudget(budget); review()
    }

    @Test
    fun fixedFlow_reachesDone_passesTokenOut_capEchoesBudget() = runTest {
        val vm = vm()
        vm.toReviewFixed("100")
        assertEquals(FollowStep.Review, vm.step)
        assertNotNull(vm.prepared)
        assertEquals("100", vm.capHuman(vm.prepared!!.verifiedGrant.capBaseUnits)) // 100 * 10^6 base units → "100"
        assertEquals(VALID_TRADER, vm.prepared!!.source) // followed trader = advisory
        assertEquals(10_000, vm.prepared!!.allocationBps)
        assertEquals(VALID_TOKEN, lastTokenOut) // fixed → the picked token flows through
        vm.confirm("pw")
        assertEquals(FollowStep.Done, vm.step)
        assertNull(vm.error)
    }

    @Test
    fun dynamicFlow_requiresAck_passesNullTokenOut() = runTest {
        val vm = vm()
        vm.enterTrader(VALID_TRADER); vm.toMode()
        assertEquals(FollowStep.ModeSelect, vm.step)
        vm.selectMode(CopyMode.DYNAMIC)
        assertFalse(vm.modeReady)        // risk not yet acknowledged
        vm.toBudget()
        assertEquals(FollowStep.ModeSelect, vm.step) // gated — cannot proceed without the ack
        vm.acknowledgeRisk(true)
        assertTrue(vm.modeReady)
        vm.toBudget(); assertEquals(FollowStep.Budget, vm.step)
        vm.enterBudget("50"); vm.review()
        assertEquals(FollowStep.Review, vm.step)
        assertTrue(capturedTokenOut)
        assertNull(lastTokenOut)          // dynamic → no fixed token (webhook derives + allowlist-gates per trade)
    }

    @Test
    fun selectingFixedAfterDynamic_resetsAck() {
        val vm = vm()
        vm.enterTrader(VALID_TRADER); vm.toMode()
        vm.selectMode(CopyMode.DYNAMIC); vm.acknowledgeRisk(true)
        vm.selectMode(CopyMode.FIXED)
        assertFalse(vm.dynRiskAck)        // switching mode clears the other-mode input
        assertFalse(vm.modeReady)         // fixed now needs a valid token
    }

    @Test
    fun fixedMode_invalidToken_blocksAtMode() {
        val vm = vm()
        vm.enterTrader(VALID_TRADER); vm.toMode()
        vm.selectMode(CopyMode.FIXED); vm.enterTokenOut("0xnothex")
        assertFalse(vm.modeReady)
        vm.toBudget()
        assertEquals(FollowStep.ModeSelect, vm.step)
        assertEquals(CopyError.INVALID_ADDRESS, vm.error)
    }

    @Test
    fun invalidTraderAddress_blocksAtScreen1() {
        val vm = vm()
        vm.enterTrader("0xnothex"); vm.toMode()
        assertEquals(FollowStep.SelectTrader, vm.step)
        assertEquals(CopyError.INVALID_ADDRESS, vm.error)
    }

    @Test
    fun selfCopy_isRejected() {
        val vm = vm()
        vm.enterTrader(OWNER.value); vm.toMode()
        assertEquals(FollowStep.SelectTrader, vm.step)
        assertEquals(CopyError.SELF_COPY, vm.error)
    }

    @Test
    fun verifyFailure_isFailClosed_noPreparedNoAdvance() = runTest {
        val vm = vm(prepareGrant = { _, _, _ -> throw RuntimeException("mismatch") })
        vm.enterTrader(VALID_TRADER); vm.toMode()
        vm.selectMode(CopyMode.FIXED); vm.enterTokenOut(VALID_TOKEN); vm.toBudget()
        vm.enterBudget("100"); vm.review()
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
        vm.toReviewFixed("100")
        vm.confirm("bad")
        assertEquals(CopyError.WRONG_PASSWORD, vm.error)
        assertEquals(FollowStep.Review, vm.step)
    }

    @Test
    fun editingBudget_clearsPriorReview() = runTest {
        val vm = vm()
        vm.toReviewFixed("100")
        assertNotNull(vm.prepared)
        vm.back() // → Budget
        vm.enterBudget("200")
        assertNull(vm.prepared) // must re-verify
        assertEquals(FollowStep.Budget, vm.step)
    }
}
