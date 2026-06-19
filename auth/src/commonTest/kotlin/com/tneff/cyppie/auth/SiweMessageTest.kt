package com.tneff.cyppie.auth

import com.tneff.cyppie.evm.EvmAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiweMessageTest {
    private val address = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")

    @Test
    fun canonical_matchesEip4361_exactly() {
        val message = SiweMessage.forSignIn(address, nonce = "abc123XYZ", issuedAt = "2026-06-19T19:00:00Z")
        val expected = buildString {
            append("auth.cyppie.com wants you to sign in with your Ethereum account:\n")
            append("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266\n\n")
            append("Sign in to Cyppie\n\n")
            append("URI: https://auth.cyppie.com\n")
            append("Version: 1\n")
            append("Chain ID: 1\n")
            append("Nonce: abc123XYZ\n")
            append("Issued At: 2026-06-19T19:00:00Z")
        }
        assertEquals(expected, message.canonical())
    }

    @Test
    fun forSignIn_usesAuthDomain_andNonEmptyStatement() {
        val message = SiweMessage.forSignIn(address, nonce = "n", issuedAt = "t")
        assertEquals("auth.cyppie.com", message.domain) // domain-binding (NOT cyppie.com)
        assertTrue(message.statement.isNotEmpty())      // statement must be non-empty
        assertEquals(1L, message.chainId)
    }
}
