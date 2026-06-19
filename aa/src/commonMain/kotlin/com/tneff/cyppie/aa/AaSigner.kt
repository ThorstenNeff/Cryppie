package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.tx.UserOpSigner

/**
 * On-device AA signing **orchestration** (PRD-05 Ph1). The security-critical sign primitive lives ONCE in
 * `:wallet` ([UserOpSigner], KAN-140) — owner-bind (no-blind), 65-byte recoverable `r‖s‖v`, and the
 * fresh-seed-per-signature key zeroize. `AaSigner` only **delegates** to it (no crypto of its own) and adds
 * the AA orchestration: build the key manager from the re-auth [SeedSource], hex-encode, and zeroize the
 * seed source after signing. The same primitive serves the DCA `userOpHash`, the one-time EIP-7702
 * authorization tuple, and the session-enable owner-userOp hash (all backend-supplied 32-byte digests).
 *
 * 🔒 Key-path (ADR-0015): only the 32-byte digest in / the 65-byte signature out — the seed never leaves
 * `:wallet`. The fresh [SeedSource] is zeroized immediately after signing (`close()`), even on failure.
 */
class AaSigner(private val accountIndex: Int = UserOpSigner.OWNER_ACCOUNT_INDEX) {

    /**
     * Signs the 32-byte [digest] for the disclosed [expectedOwner] (= the SCA/SIWE-identity account); the
     * delegate verifies the signing account derives to [expectedOwner] (no blind signing). Returns the
     * `0x`-prefixed 65-byte signature. [seedSource] is zeroized after signing.
     */
    fun signDigest(digest: ByteArray, expectedOwner: EvmAddress, seedSource: SeedSource): String {
        val bytes = try {
            UserOpSigner(EvmKeyManager(seedSource)).sign(digest, expectedOwner, accountIndex)
        } finally {
            (seedSource as? AutoCloseable)?.close()
        }
        return "0x" + Hex.encode(bytes)
    }

    /** Convenience: decode a `0x`-prefixed hex [digestHex] (e.g. [PendingDca.userOpHash]) then sign. */
    fun signDigest(digestHex: String, expectedOwner: EvmAddress, seedSource: SeedSource): String {
        val bytes = Hex.decodeOrNull(digestHex.removePrefix("0x"))
            ?: run { (seedSource as? AutoCloseable)?.close(); throw IllegalArgumentException("digest not valid hex") }
        return signDigest(bytes, expectedOwner, seedSource)
    }
}
