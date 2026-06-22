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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StrategyListViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val seed: SeedSource = SeedSource.ofSeed(ByteArray(64))

    private fun session(id: String, status: String = "active") = StrategySession(
        sessionId = id, name = "Balanced", budgetBaseUnits = "100000000", driftBps = 250, status = status,
        lastRebalanceEpochSeconds = 1_700_000_000L,
        targets = listOf(BasketTarget("0x" + "a".repeat(40), 60), BasketTarget("0x" + "b".repeat(40), 40)),
    )

    private fun vm(
        list: suspend () -> List<StrategySession> = { listOf(session("0x01")) },
        revoke: suspend (StrategySession, SeedSource) -> Unit = { _, _ -> },
        reauth: suspend (String) -> SeedSource? = { seed },
        setPaused: suspend (StrategySession, Boolean) -> Unit = { _, _ -> },
    ) = StrategyListViewModel(listStrategies = list, revokeStrategy = revoke, reauth = reauth, setPaused = setPaused)

    @Test
    fun openDetail_thenTogglePause_flipsStatus() = runTest {
        var requestedPaused: Boolean? = null
        val m = vm(list = { listOf(session("0x01", status = "active")) }, setPaused = { _, p -> requestedPaused = p })
        m.load()
        m.openDetail(session("0x01"))
        assertEquals("0x01", m.detailTarget?.sessionId)
        m.togglePause() // active → pause
        assertEquals(true, requestedPaused)
        assertEquals("paused", m.detailTarget?.status)
        assertEquals("paused", m.sessions.first().status) // list row updated too
    }

    @Test
    fun togglePause_failure_keepsStatus() = runTest {
        val m = vm(list = { listOf(session("0x01", status = "active")) }, setPaused = { _, _ -> throw IllegalStateException("net") })
        m.load(); m.openDetail(session("0x01")); m.togglePause()
        assertEquals("active", m.detailTarget?.status) // unchanged on failure
    }

    @Test
    fun closeDetail_clearsTarget() = runTest {
        val m = vm()
        m.load(); m.openDetail(session("0x01")); m.closeDetail()
        assertNull(m.detailTarget)
    }

    @Test
    fun load_loaded() = runTest {
        val m = vm(list = { listOf(session("0x01"), session("0x02")) })
        m.load()
        assertEquals(StratListState.Loaded, m.listState)
        assertEquals(2, m.sessions.size)
    }

    @Test
    fun load_empty() = runTest {
        val m = vm(list = { emptyList() })
        m.load()
        assertEquals(StratListState.Empty, m.listState)
    }

    @Test
    fun load_error_notSilentEmpty() = runTest {
        val m = vm(list = { throw IllegalStateException("net") })
        m.load()
        assertEquals(StratListState.Error, m.listState)
    }

    @Test
    fun confirmRevoke_success_dropsRow() = runTest {
        val m = vm(list = { listOf(session("0x01"), session("0x02")) })
        m.load(); m.askRevoke(session("0x01")); m.confirmRevoke("pw")
        assertNull(m.revokeTarget)
        assertTrue(m.lastRevoked)
        assertEquals(listOf("0x02"), m.sessions.map { it.sessionId })
    }

    @Test
    fun confirmRevoke_wrongPassword_keepsRow_failSafe() = runTest {
        val m = vm(list = { listOf(session("0x01")) }, reauth = { null })
        m.load(); m.askRevoke(session("0x01")); m.confirmRevoke("bad")
        assertEquals(StratRevokeError.WrongPassword, m.revokeError)
        assertEquals(listOf("0x01"), m.sessions.map { it.sessionId })
        assertFalse(m.lastRevoked)
    }

    @Test
    fun confirmRevoke_failure_keepsRow_failSafe() = runTest {
        val m = vm(list = { listOf(session("0x01")) }, revoke = { _, _ -> throw IllegalStateException("reverted") })
        m.load(); m.askRevoke(session("0x01")); m.confirmRevoke("pw")
        assertEquals(StratRevokeError.Failed, m.revokeError)
        assertEquals(listOf("0x01"), m.sessions.map { it.sessionId })
    }
}
