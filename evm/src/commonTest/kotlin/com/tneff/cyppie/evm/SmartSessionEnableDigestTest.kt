package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.SmartSessionEnableDigest.ActionData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.PolicyData
import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Byte-exact pins of the on-device enable digest against the backend's regenerated reference vectors
 * (`Backend/aa-trigger/scripts/enable-vector.mjs`, SDK==raw-viem), with the **C3-corrected placement**
 * (KAN-150): TimeFrame window in `userOpPolicies`; the spending-limit cap on the **token-approve action**;
 * the swap as a separate time-boxed action. The policy `initData` are the **real audited helper outputs**
 * (`getSpendingLimitsPolicy` = `abi.encode(address[],uint256[])`; `getTimeFramePolicy` =
 * `encodePacked(uint48 validUntil, uint48 validAfter)`). A mismatch means the app would sign a digest the
 * on-chain session can't validate.
 */
class SmartSessionEnableDigestTest {

    // Shared literals (= enable-vector.mjs).
    private val account = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val salt = "0x0000000000000000000000000000000000000000000000000000000000000001"
    private val nonce = "0"
    private val usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private val router = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"
    private val approveSelector = "0x095ea7b3"
    private val swapSelector = "0x5ae401dc"

    // A/B/C use placeholder validator + validatorInitData (isolate the typed-data encoding from real addrs).
    private val placeholderValidator = "0x0000000000000000000000000000000000000777"
    private val placeholderValidatorInitData = "0x000000000000000000000000cafecafecafecafecafecafecafecafecafecafe"

    // Real audited policy initData (the helper outputs — same bytes for B/C/D; only the addresses differ).
    private val spendInitData = "0x" +
        "0000000000000000000000000000000000000000000000000000000000000040" + // offset tokens[]
        "0000000000000000000000000000000000000000000000000000000000000080" + // offset limits[]
        "0000000000000000000000000000000000000000000000000000000000000001" + // tokens.length
        "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" + // tokens[0] = USDC
        "0000000000000000000000000000000000000000000000000000000000000001" + // limits.length
        "00000000000000000000000000000000000000000000000000000000000f4240"   // limits[0] = 1_000_000
    private val timeInitData = "0x0000687c05000000683f1a80" // encodePacked(uint48 validUntil, uint48 validAfter)

    /** The C3-corrected DCA permission shape: TimeFrame in userOpPolicies; cap on approve; swap separate. */
    private fun dcaPermissions(spendPolicy: String, timePolicy: String, paymaster: Boolean) = SignedPermissions(
        permitERC4337Paymaster = paymaster,
        userOpPolicies = listOf(PolicyData(timePolicy, timeInitData)),
        actions = listOf(
            ActionData(approveSelector, usdc, listOf(PolicyData(spendPolicy, spendInitData))),
            ActionData(swapSelector, router, listOf(PolicyData(timePolicy, timeInitData))),
        ),
    )

    /** Digest with the placeholder validator (A/B/C). */
    private fun digest(chainId: Long, permissions: SignedPermissions): String = Hex.encode(
        SmartSessionEnableDigest.enableDigest(
            account = account, chainId = chainId, sessionValidator = placeholderValidator,
            sessionValidatorInitData = placeholderValidatorInitData, salt = salt, nonce = nonce, permissions = permissions,
        ),
    )

    // ── Vector A — minimal (no policies/actions): isolates the typed-data encoding (unchanged by the placement) ──
    private val permissionsA = SignedPermissions()

    @Test
    fun vectorA_chain1() {
        assertEquals("c21b5f1771f70475cff1d6ee9a69a8e7575b984f7b31a009bf497a57ee4a62fc", digest(1L, permissionsA))
    }

    @Test
    fun vectorA_base() {
        assertEquals("4e72e4786fd4b20eadd3211939947907da495ef97899faad30ce99801aa1f76b", digest(8453L, permissionsA))
    }

    // ── Vector B — corrected DCA shape, placeholder policy addrs, paymaster=false ──
    private val permissionsB = dcaPermissions("0x0000000000000000000000000000000000000511", "0x0000000000000000000000000000000000000522", paymaster = false)

    @Test
    fun vectorB_chain1() {
        assertEquals("830c6b3c70ba344b258eddbfde6e44ffba3d4956d346e7c240df8a10f257873b", digest(1L, permissionsB))
    }

    @Test
    fun vectorB_base() {
        assertEquals("e129263b87b27aaf5273ac967f0f67252107f89cb0774ef752950dc79efdcc18", digest(8453L, permissionsB))
    }

    // ── Vector C — corrected DCA shape SPONSORED (= B but permitERC4337Paymaster=true) ──
    private val permissionsC = permissionsB.copy(permitERC4337Paymaster = true)

    @Test
    fun vectorC_paymasterTrue_chain1() {
        assertEquals("f6947f9e980925c4eb1fdd85a6d1a0ba92c3dc582bfc102cc971b763eebb2b27", digest(1L, permissionsC))
    }

    @Test
    fun vectorC_paymasterTrue_base() {
        assertEquals("1eb4a4a66ba6e71fc2013ec2ecec50777b593c50bd5ea660cfda31193bf42b39", digest(8453L, permissionsC))
    }

    @Test
    fun paymasterFlagChangesDigest() {
        assertEquals(false, digest(1L, permissionsB) == digest(1L, permissionsC))
    }

    // ── Vector D — the REAL DCA pin: real OwnableValidator + GLOBAL_CONSTANTS policies + paymaster=true ──
    private val validatorD = "0x000000000013fdB5234E4E3162a810F54d9f7E98"
    private val validatorInitDataD = "0x" +
        "0000000000000000000000000000000000000000000000000000000000000001" + // threshold = 1
        "0000000000000000000000000000000000000000000000000000000000000040" + // offset to owners[]
        "0000000000000000000000000000000000000000000000000000000000000001" + // owners.length = 1
        "000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb92266"    // owners[0] = account
    private val permissionsD = dcaPermissions(
        "0x000000000033212e272655d8a22402db819477a6", // real SpendingLimits (GLOBAL_CONSTANTS)
        "0x0000000000D30f611fA3bf652ac6879428586930", // real TimeFrame (GLOBAL_CONSTANTS)
        paymaster = true,
    )

    private fun digestD(chainId: Long): String = Hex.encode(
        SmartSessionEnableDigest.enableDigest(
            account = account, chainId = chainId, sessionValidator = validatorD,
            sessionValidatorInitData = validatorInitDataD, salt = salt, nonce = nonce, permissions = permissionsD,
        ),
    )

    @Test
    fun vectorD_realDcaPin_chain1() {
        assertEquals("8be4818ec1fb3068b671a68158eef275f922735658a18908069fe29225898c5c", digestD(1L))
    }

    @Test
    fun vectorD_realDcaPin_base() {
        assertEquals("dacaa17a34fbd97bb7764de7e9f4a79517f22f239d513f3d28753c7762df99f8", digestD(8453L))
    }

    @Test
    fun chainBindingChangesDigest() {
        assertEquals(false, digestD(1L) == digestD(8453L))
    }

    @Test
    fun unsupportedChainFailsClosed() {
        assertFailsWith<IllegalArgumentException> { digest(10L, permissionsA) }
    }
}
