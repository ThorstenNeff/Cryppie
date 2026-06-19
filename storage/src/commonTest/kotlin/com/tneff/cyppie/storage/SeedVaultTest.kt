package com.tneff.cyppie.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeedVaultTest {

    private val seed = ByteArray(64) { (it + 1).toByte() } // stand-in 64-byte BIP-39 seed
    private val password = "correct horse battery staple"

    @Test
    fun roundTripStoreThenUnlock() = runTest {
        val vault = SeedVault(CiphertextStore.inMemory())
        vault.store(seed.copyOf(), password.toCharArray())
        assertTrue(vault.isInitialized())
        val recovered = vault.unlock(password.toCharArray()).withSeed { it.copyOf() }
        assertContentEquals(seed, recovered)
    }

    @Test
    fun storeAndOpenPersistsAndYieldsSameSeed() = runTest {
        // KAN-111: onboarding opens the session at creation. The returned source must hold exactly the
        // stored seed, and a later unlock with the password must recover the same bytes (persisted).
        val vault = SeedVault(CiphertextStore.inMemory())
        val opened = vault.storeAndOpen(seed.copyOf(), password.toCharArray())
        assertTrue(vault.isInitialized())
        assertContentEquals(seed, opened.withSeed { it.copyOf() })
        assertContentEquals(seed, vault.unlock(password.toCharArray()).withSeed { it.copyOf() })
    }

    @Test
    fun storeAndOpenSourceIsIndependentOfCallerSeed() = runTest {
        // The opened source owns a private copy: zeroizing the caller's buffer must not affect it.
        val vault = SeedVault(CiphertextStore.inMemory())
        val callerSeed = seed.copyOf()
        val opened = vault.storeAndOpen(callerSeed, password.toCharArray())
        callerSeed.fill(0)
        assertContentEquals(seed, opened.withSeed { it.copyOf() })
    }

    @Test
    fun wrongPasswordFails() = runTest {
        val vault = SeedVault(CiphertextStore.inMemory())
        vault.store(seed.copyOf(), password.toCharArray())
        assertFailsWith<StorageException.InvalidPassword> { vault.unlock("wrong password".toCharArray()) }
    }

    @Test
    fun tamperedCiphertextFails() = runTest {
        val store = CiphertextStore.inMemory()
        SeedVault(store).store(seed.copyOf(), password.toCharArray())
        val blob = store.read()!!
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte() // flip a byte in the GCM tag
        store.write(blob)
        assertFailsWith<StorageException.InvalidPassword> { SeedVault(store).unlock(password.toCharArray()) }
    }

    @Test
    fun unknownVersionIsCorruptData() = runTest {
        val store = CiphertextStore.inMemory()
        SeedVault(store).store(seed.copyOf(), password.toCharArray())
        val blob = store.read()!!
        blob[0] = 0x09 // bogus version header
        store.write(blob)
        assertFailsWith<StorageException.CorruptData> { SeedVault(store).unlock(password.toCharArray()) }
    }

    @Test
    fun unlockBeforeStoreThrowsNotInitialized() = runTest {
        assertFailsWith<StorageException.NotInitialized> {
            SeedVault(CiphertextStore.inMemory()).unlock(password.toCharArray())
        }
    }

    @Test
    fun eachStoreUsesFreshSaltAndIv() = runTest {
        val a = CiphertextStore.inMemory().also { SeedVault(it).store(seed.copyOf(), password.toCharArray()) }
        val b = CiphertextStore.inMemory().also { SeedVault(it).store(seed.copyOf(), password.toCharArray()) }
        assertFalse(a.read()!!.contentEquals(b.read()!!)) // no clear-seed reuse; random salt+iv
    }

    @Test
    fun unlockedSeedSourceIsCloseableAndZeroizes() = runTest {
        val vault = SeedVault(CiphertextStore.inMemory())
        vault.store(seed.copyOf(), password.toCharArray())
        val source = vault.unlock(password.toCharArray())
        assertContentEquals(seed, source.withSeed { it.copyOf() })
        source.close() // zeroizes the held seed
        assertFailsWith<IllegalStateException> { source.withSeed { it } }
    }

    @Test
    fun noopKeyStoreReportsBiometricsUnavailable() = runTest {
        val vault = SeedVault(CiphertextStore.inMemory())
        vault.store(seed.copyOf(), password.toCharArray())
        vault.enableBiometricUnlock(password.toCharArray()) // no-op without hardware
        assertFailsWith<StorageException.KeyStoreUnavailable> { vault.unlockWithBiometrics() }
    }
}
