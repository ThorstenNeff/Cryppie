package com.tneff.cyppie.evm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-160 — byte-exact pin of the EIP-7702 authorization digest against viem's `hashAuthorization`, plus the
 * fail-closed delegate-target/chainId pins that stop a malicious first-enable from taking over the account.
 */
class Eip7702AuthorizationTest {

    private val kernel = Eip7702Authorization.KERNEL_V3_3_IMPLEMENTATION

    @Test
    fun authorizationDigest_matchesBackendVector() {
        // Backend auth7702-vector.mjs (nonce=0), 2-source verified (SDK + on-chain getCode). CREATE2 ⇒ same delegate, all chains.
        assertEquals(
            "277da3848b6154880dc13cebd5d5c0561fba74538ed0df07872871fc611f307c", // ETH(1)
            Hex.encode(Eip7702Authorization.authorizationDigest(1L, kernel, 0L)),
        )
        assertEquals(
            "949b51ff7d9fd8771fe3391c8e48ac208195590ea744bfe95ea9b3f5bbc0d0bc", // Base(8453)
            Hex.encode(Eip7702Authorization.authorizationDigest(8453L, kernel, 0L)),
        )
        assertEquals(
            "2ae929e109570ef110f255d51751661454598e1a1f2f747a2a0cc364d15e774b", // Base Sepolia(84532)
            Hex.encode(Eip7702Authorization.authorizationDigest(84532L, kernel, 0L)),
        )
        // non-zero nonce (RLP minimal-integer encoding, vs viem):
        assertEquals(
            "b4261391074afebf8897dc89c66fd1f625a48611c81379de30bab87ddd8d6cd1",
            Hex.encode(Eip7702Authorization.authorizationDigest(1L, kernel, 255L)),
        )
    }

    @Test
    fun verify_returnsDigest_forPinnedTargetAndChain() {
        val digest = Eip7702Authorization.verify(chainId = 1L, address = kernel, nonce = 0L, expectedChainId = 1L)
        assertEquals("277da3848b6154880dc13cebd5d5c0561fba74538ed0df07872871fc611f307c", Hex.encode(digest))
    }

    @Test
    fun verify_failsClosed_onRogueDelegateTarget() {
        assertFailsWith<AuthorizationVerificationException> {
            Eip7702Authorization.verify(1L, "0x000000000000000000000000000000000000dEaD", 0L, 1L)
        }
    }

    @Test
    fun verify_failsClosed_onChainMismatch() {
        assertFailsWith<AuthorizationVerificationException> {
            Eip7702Authorization.verify(chainId = 8453L, address = kernel, nonce = 0L, expectedChainId = 1L)
        }
    }
}
