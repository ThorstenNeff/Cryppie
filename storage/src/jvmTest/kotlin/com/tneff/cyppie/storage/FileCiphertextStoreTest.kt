package com.tneff.cyppie.storage

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileCiphertextStoreTest {

    private fun tempPath() =
        File(System.getProperty("java.io.tmpdir"), "cyppie-kan95-${System.nanoTime()}/wallet.seed").path

    @Test
    fun roundTripsWriteReadOverwriteClear() = runTest {
        val store = CiphertextStore.file(tempPath())
        assertNull(store.read()) // nothing yet

        store.write(byteArrayOf(1, 2, 3, 4))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), store.read())

        store.write(byteArrayOf(9, 8)) // atomic overwrite (temp + rename)
        assertContentEquals(byteArrayOf(9, 8), store.read())

        store.clear()
        assertNull(store.read())
    }

    @Test
    fun defaultSeedPathIsNonEmptyAndStable() {
        val a = defaultSeedFilePath()
        assertTrue(a.isNotEmpty())
        assertTrue(a.endsWith("wallet.seed"))
        kotlin.test.assertEquals(a, defaultSeedFilePath()) // deterministic
    }
}
