package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KAN-143 — the on-device Smart-Sessions ENABLE-digest recompute must reproduce the backend's 4 reference
 * vectors **exactly** (A-minimal + B-dca, each on chainId 1 and 8453), proven SDK==raw-viem in
 * `Backend/aa-trigger/scripts/enable-vector.mjs`. A byte-mismatch on any of the four means the Kotlin EIP-712
 * encoding of the `MultiChainSession` type table diverges from `@rhinestone/module-sdk` 0.3.1.
 */
class SmartSessionEnableDigestTest {

    // Fixed literal inputs shared by both vectors (= the backend spec / enable-vector.mjs).
    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val sessionValidator = "0x0000000000000000000000000000000000000777"
    private val sessionValidatorInitData = "0x000000000000000000000000cafecafecafecafecafecafecafecafecafecafe"
    private val salt = "0x0000000000000000000000000000000000000000000000000000000000000001"
    private val nonce = "0"

    private fun digest(chainId: Long, permissions: SignedPermissions): String =
        Hex.encode(
            SmartSessionEnableDigest.enableDigest(
                account = account,
                chainId = chainId,
                sessionValidator = sessionValidator,
                sessionValidatorInitData = sessionValidatorInitData,
                salt = salt,
                nonce = nonce,
                permissions = permissions,
            ),
        )

    // ── Vector A — minimal (no userOpPolicies, no actions) ──
    private val permissionsA = SignedPermissions()

    @Test
    fun vectorA_chain1() {
        assertEquals("c21b5f1771f70475cff1d6ee9a69a8e7575b984f7b31a009bf497a57ee4a62fc", digest(1L, permissionsA))
    }

    @Test
    fun vectorA_base() {
        assertEquals("4e72e4786fd4b20eadd3211939947907da495ef97899faad30ce99801aa1f76b", digest(8453L, permissionsA))
    }

    // ── Vector B — realistic DCA (one spending-limit userOpPolicy + one swap action w/ time-frame policy) ──
    private val permissionsB = SignedPermissions(
        userOpPolicies = listOf(
            PolicyData(
                policy = "0x0000000000000000000000000000000000000511", // spending-limit policy
                // USDC (0xa0b8…eb48), cap 1_000_000 (0x0f4240)
                initData = "0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" +
                    "00000000000000000000000000000000000000000000000000000000000f4240",
            ),
        ),
        actions = listOf(
            ActionData(
                actionTargetSelector = "0x5ae401dc", // multicall(uint256,bytes[])
                actionTarget = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45", // UniV3 router (example)
                actionPolicies = listOf(
                    PolicyData(
                        policy = "0x0000000000000000000000000000000000000522", // time-frame policy
                        initData = "0x00000000000000000000000000000000000000000000000000000000683f9e80" +
                            "00000000000000000000000000000000000000000000000000000000687a4f00",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun vectorB_chain1() {
        assertEquals("4b7fe8e3ab2cf1929e70dda85aee35b48a005ce95a0d73a94f2221351ce62632", digest(1L, permissionsB))
    }

    @Test
    fun vectorB_base() {
        assertEquals("612b0831cbca241f4726678f3f8a17db5de2eb6838447f416514b635331b9ddd", digest(8453L, permissionsB))
    }

    @Test
    fun chainBindingChangesDigest() {
        // chainId 1 vs 8453 must differ (binding lives in ChainSession.chainId, not the domain).
        assertEquals(false, digest(1L, permissionsA) == digest(8453L, permissionsA))
    }

    @Test
    fun unsupportedChainFailsClosed() {
        assertFailsWith<IllegalArgumentException> { digest(10L, permissionsA) } // Optimism not in the AA stack
    }
}
