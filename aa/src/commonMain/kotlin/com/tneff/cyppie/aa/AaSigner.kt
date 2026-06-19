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
            zeroize(seedSource)
        }
        return "0x" + Hex.encode(bytes)
    }

    /** Convenience: decode a `0x`-prefixed hex [digestHex] (e.g. [PendingDca.userOpHash]) then sign. */
    fun signDigest(digestHex: String, expectedOwner: EvmAddress, seedSource: SeedSource): String {
        val bytes = Hex.decodeOrNull(digestHex.removePrefix("0x"))
            ?: run { zeroize(seedSource); throw IllegalArgumentException("digest not valid hex") }
        return signDigest(bytes, expectedOwner, seedSource)
    }

    /**
     * P2 (zeroize-cast harden): the single seed-zeroize path. A re-auth [SeedSource] is a closeable
     * `SecureSeedSource` (M1) — close it. A NON-closeable source would silently skip zeroize, which for the
     * signing path is a key-material leak, so we fail loudly in debug rather than pass it by; only the
     * deliberate ambient-session read wrapper (never the signing path) is non-closeable, and it never
     * reaches here. (Interface-wide zeroization seam = KAN-71.)
     */
    private fun zeroize(seedSource: SeedSource) {
        val closeable = seedSource as? AutoCloseable
        check(closeable != null) { "AaSigner seed source is not zeroizable — refusing to leak key material" }
        closeable.close()
    }
}
