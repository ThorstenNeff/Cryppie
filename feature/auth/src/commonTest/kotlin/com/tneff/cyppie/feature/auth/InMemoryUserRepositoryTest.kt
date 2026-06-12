package com.tneff.cyppie.feature.auth

import com.tneff.cyppie.feature.auth.data.InMemoryUserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryUserRepositoryTest {

    @Test
    fun registerSucceedsForNewEmail() {
        val repo = InMemoryUserRepository()
        val result = repo.register("Ada", "ada@example.com", "secret1")
        assertTrue(result.isSuccess)
        assertEquals("ada@example.com", result.getOrThrow().email)
    }

    @Test
    fun registerFailsForDuplicateEmail() {
        val repo = InMemoryUserRepository()
        repo.register("Ada", "ada@example.com", "secret1")
        val duplicate = repo.register("Ada Two", "ADA@example.com", "secret2")
        assertTrue(duplicate.isFailure)
    }

    @Test
    fun loginSucceedsWithCorrectCredentials() {
        val repo = InMemoryUserRepository()
        repo.register("Ada", "ada@example.com", "secret1")
        val result = repo.login("ada@example.com", "secret1")
        assertTrue(result.isSuccess)
        assertEquals("Ada", result.getOrThrow().name)
    }

    @Test
    fun loginIsCaseInsensitiveForEmail() {
        val repo = InMemoryUserRepository()
        repo.register("Ada", "ada@example.com", "secret1")
        val result = repo.login("  ADA@Example.com ", "secret1")
        assertTrue(result.isSuccess)
    }

    @Test
    fun loginFailsWithWrongPassword() {
        val repo = InMemoryUserRepository()
        repo.register("Ada", "ada@example.com", "secret1")
        val result = repo.login("ada@example.com", "wrong")
        assertTrue(result.isFailure)
    }

    @Test
    fun loginFailsForUnknownEmail() {
        val repo = InMemoryUserRepository()
        val result = repo.login("nobody@example.com", "secret1")
        assertTrue(result.isFailure)
    }
}
