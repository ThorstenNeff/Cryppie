package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource

/**
 * On-device AA signer (PRD-05 Ph1). Signs the **backend-supplied 32-byte `digestToSign`** with the SCA
 * owner key — the SIWE-identity account ([accountIndex] = 0 for the MVP, Q5). The backend confirmed the
 * minimal-crypto split: the app raw-signs only the digest → 65-byte; **no userOp packing, no EIP-191
 * prefix, no Kernel envelope in the app** (`aa-trigger` wraps everything). The SAME primitive covers all
 * three Ph1 digests: the DCA `userOpHash`, the one-time EIP-7702 authorization tuple, and the
 * session-enable owner-userOp hash. Reuses the `:wallet` primitive ([EvmKeyManager.sign]) + the Send/WC
 * security pattern.
 *
 * 🔒 Key-path (ADR-0015): only the 32-byte digest comes in, only the 65-byte signature goes out — the
 * seed/private key never leaves `:wallet`. The fresh [SeedSource] (from re-auth) is **zeroized
 * immediately after signing** (`close()`), even when signing throws (mirrors `SendOrchestrator` M1).
 */
class AaSigner(private val accountIndex: Int = 0) {

    /**
     * Signs the 32-byte [digest] and returns the `0x`-prefixed 65-byte signature `r‖s‖v` (v = recId + 27).
     * [seedSource] is zeroized immediately after signing.
     */
    fun signDigest(digest: ByteArray, seedSource: SeedSource): String {
        if (digest.size != 32) {
            // Zeroize even on bad input (fail-closed): the caller handed us a fresh unlocked seed.
            (seedSource as? AutoCloseable)?.close()
            throw IllegalArgumentException("digest must be 32 bytes, was ${digest.size}")
        }
        val sig = try {
            EvmKeyManager(seedSource).sign(accountIndex, digest)
        } finally {
            (seedSource as? AutoCloseable)?.close()
        }
        val v = (sig.recId + 27).toByte()
        return "0x" + Hex.encode(sig.r + sig.s + byteArrayOf(v))
    }

    /** Convenience: decode a `0x`-prefixed hex [digestHex] (e.g. [PendingDca.userOpHash]) then sign. */
    fun signDigest(digestHex: String, seedSource: SeedSource): String {
        val bytes = Hex.decodeOrNull(digestHex.removePrefix("0x"))
            ?: run { (seedSource as? AutoCloseable)?.close(); throw IllegalArgumentException("digest not valid hex") }
        return signDigest(bytes, seedSource)
    }
}
