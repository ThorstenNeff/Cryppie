package com.tneff.cyppie.feature.strat

import com.tneff.cyppie.aa.BuiltStrategyEnable
import com.tneff.cyppie.aa.StrategyGrantPreview
import com.tneff.cyppie.aa.StrategyWeight
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import com.tneff.cyppie.evm.TokenCap
import com.tneff.cyppie.evm.VerifiedBasketGrant
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

private val TOKEN_A = "0x" + "a".repeat(40)
private val TOKEN_B = "0x" + "b".repeat(40)
private val ROUTER = "0x" + "e".repeat(40)

@OptIn(ExperimentalCoroutinesApi::class)
class StrategyViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val seed: SeedSource = SeedSource.ofSeed(ByteArray(64))

    // The :aa StrategyGrantPreview (KAN-165): 🔒 verifiedGrant (per-token sell-caps) + ℹ️ advisory weights + the
    // enable. The VM only stores it + hands `enable` to the (faked) authorizeGrant, so a minimal enable is fine.
    private fun preview(targets: List<BasketTarget>, budget: String): StrategyGrantPreview {
        val weights = targets.map { StrategyWeight(it.token, it.weightPercent * 100) }
        return StrategyGrantPreview(
            verifiedGrant = VerifiedBasketGrant(
                account = "0x" + "f".repeat(40), chainId = 1L, actionTarget = ROUTER, actionSelector = "0x5ae401dc",
                caps = targets.map { TokenCap(it.token, budget) }, windowStartEpochSeconds = 0L, windowEndEpochSeconds = 100L,
            ),
            weights = weights,
            enable = BuiltStrategyEnable(
                digestToSign = "0x", account = "0x" + "f".repeat(40), chainId = 1L, sessionValidator = "0x",
                sessionValidatorInitData = "0x", salt = "0x", nonce = "0", permissionId = "0x",
                permissions = SignedPermissions(permitERC4337Paymaster = true), capTokens = targets.map { it.token },
            ),
        )
    }

    private fun vm(
        prepare: suspend (List<BasketTarget>, String) -> StrategyGrantPreview = { t, b -> preview(t, b) },
        authorize: suspend (StrategyGrantPreview, SeedSource) -> Unit = { _, _ -> },
        reauth: suspend (String) -> SeedSource? = { seed },
    ) = StrategyViewModel(budgetTokenDecimals = 6, prepareGrant = prepare, authorizeGrant = authorize, reauth = reauth)

    /** Fill two valid tokens 60/40 + a budget → setupReady. */
    private fun StrategyViewModel.fillValid() {
        setToken(0, TOKEN_A); setWeight(0, "60")
        setToken(1, TOKEN_B); setWeight(1, "40")
        enterBudget("100")
    }

    @Test
    fun setupReady_requires100PercentAndBudget() = runTest {
        val m = vm()
        assertEquals(false, m.setupReady)
        m.setToken(0, TOKEN_A); m.setWeight(0, "60")
        m.setToken(1, TOKEN_B); m.setWeight(1, "30") // total 90
        m.enterBudget("100")
        assertEquals(90, m.totalWeight)
        assertEquals(false, m.setupReady)
        m.setWeight(1, "40") // total 100
        assertTrue(m.setupReady)
    }

    @Test
    fun review_validAllocation_reachesReviewWithPreview() = runTest {
        val m = vm()
        m.fillValid()
        m.review()
        assertEquals(StratStep.Review, m.step)
        assertNotNull(m.prepared)
        assertEquals(2, m.prepared!!.verifiedGrant.caps.size)
    }

    @Test
    fun review_sumNot100_blocksWithError() = runTest {
        val m = vm()
        m.setToken(0, TOKEN_A); m.setWeight(0, "50")
        m.setToken(1, TOKEN_B); m.setWeight(1, "30")
        m.enterBudget("100")
        m.review()
        assertEquals(StratError.SUM_NOT_100, m.error)
        assertEquals(StratStep.Setup, m.step)
    }

    @Test
    fun review_minTokens_blocks() = runTest {
        val m = vm()
        m.setToken(0, TOKEN_A); m.setWeight(0, "100")
        m.enterBudget("100")
        m.review()
        assertEquals(StratError.MIN_TOKENS, m.error)
    }

    @Test
    fun confirm_success_reachesDone() = runTest {
        val m = vm()
        m.fillValid(); m.review()
        m.confirm("pw")
        assertEquals(StratStep.Done, m.step)
        assertNull(m.error)
    }

    @Test
    fun confirm_wrongPassword_isFailClosed() = runTest {
        val m = vm(reauth = { null })
        m.fillValid(); m.review()
        m.confirm("bad")
        assertEquals(StratError.WRONG_PASSWORD, m.error)
        assertEquals(StratStep.Review, m.step) // never advanced to Done
    }

    @Test
    fun editingAfterReview_invalidatesPriorPreview() = runTest {
        val m = vm()
        m.fillValid(); m.review()
        assertNotNull(m.prepared)
        m.backToSetup()
        m.setWeight(0, "70") // edit → prior preview cleared
        assertNull(m.prepared)
    }
}
