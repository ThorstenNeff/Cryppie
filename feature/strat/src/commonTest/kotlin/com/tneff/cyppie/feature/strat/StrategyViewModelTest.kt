package com.tneff.cyppie.feature.strat

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

    private fun preview(targets: List<BasketTarget>, budget: String) = StrategyGrantPreview(
        sellCapBaseUnits = budget, basketTokens = targets.map { it.token }, router = ROUTER,
        actionSelector = "0x5ae401dc", windowStartEpochSeconds = 0L, windowEndEpochSeconds = 100L, targets = targets,
    )

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
        assertEquals(2, m.prepared!!.basketTokens.size)
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
