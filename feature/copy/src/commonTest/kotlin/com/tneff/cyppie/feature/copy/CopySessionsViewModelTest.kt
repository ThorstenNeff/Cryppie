package com.tneff.cyppie.feature.copy

import com.tneff.cyppie.aa.CopySession
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
class CopySessionsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun session(id: String, status: String = "active") = CopySession(
        permissionId = id, chainId = 1L, source = "0x" + "a".repeat(40), token = "0x" + "d".repeat(40),
        cap = "30000000", used = "10000000", remaining = "20000000", status = status, since = 1_700_000_000L,
        router = "0x" + "e".repeat(40),
    )

    private val seed: SeedSource = SeedSource.ofSeed(ByteArray(64))

    private fun vm(
        list: suspend () -> List<CopySession> = { listOf(session("0x01")) },
        revoke: suspend (CopySession, SeedSource) -> Unit = { _, _ -> },
        reauth: suspend (String) -> SeedSource? = { seed },
    ) = CopySessionsViewModel(listSessions = list, revokeSession = revoke, reauth = reauth)

    @Test
    fun load_populatesAndMarksLoaded() = runTest {
        val m = vm(list = { listOf(session("0x01"), session("0x02")) })
        m.load()
        assertEquals(CopyListState.Loaded, m.listState)
        assertEquals(2, m.sessions.size)
    }

    @Test
    fun load_emptyList_isEmptyState() = runTest {
        val m = vm(list = { emptyList() })
        m.load()
        assertEquals(CopyListState.Empty, m.listState)
        assertTrue(m.sessions.isEmpty())
    }

    @Test
    fun load_failure_isErrorState_notSilentEmpty() = runTest {
        val m = vm(list = { throw IllegalStateException("network") })
        m.load()
        assertEquals(CopyListState.Error, m.listState)
    }

    @Test
    fun confirmRevoke_success_dropsRowAndConfirms() = runTest {
        val m = vm(list = { listOf(session("0x01"), session("0x02")) })
        m.load()
        m.askRevoke(session("0x01"))
        m.confirmRevoke("pw")
        assertFalse(m.revoking)
        assertNull(m.revokeTarget)            // dialog closed
        assertTrue(m.lastRevoked)             // copy_revoked confirmation
        assertEquals(listOf("0x02"), m.sessions.map { it.permissionId }) // only the revoked row gone
    }

    @Test
    fun confirmRevoke_lastSession_goesEmpty() = runTest {
        val m = vm(list = { listOf(session("0x01")) })
        m.load()
        m.askRevoke(session("0x01"))
        m.confirmRevoke("pw")
        assertEquals(CopyListState.Empty, m.listState)
    }

    @Test
    fun confirmRevoke_wrongPassword_keepsRow_failSafe() = runTest {
        val m = vm(list = { listOf(session("0x01")) }, reauth = { null })
        m.load()
        m.askRevoke(session("0x01"))
        m.confirmRevoke("bad")
        assertEquals(RevokeError.WrongPassword, m.revokeError)
        assertEquals(listOf("0x01"), m.sessions.map { it.permissionId }) // still active
        assertFalse(m.lastRevoked)
    }

    @Test
    fun confirmRevoke_broadcastFailure_keepsRow_failSafe() = runTest {
        val m = vm(list = { listOf(session("0x01")) }, revoke = { _, _ -> throw IllegalStateException("reverted") })
        m.load()
        m.askRevoke(session("0x01"))
        m.confirmRevoke("pw")
        assertEquals(RevokeError.Failed, m.revokeError)
        assertEquals(listOf("0x01"), m.sessions.map { it.permissionId }) // never falsely "ended"
    }

    @Test
    fun dismissRevoke_clearsTargetAndError() = runTest {
        val m = vm()
        m.load()
        m.askRevoke(session("0x01"))
        m.dismissRevoke()
        assertNull(m.revokeTarget)
        assertNull(m.revokeError)
    }
}
