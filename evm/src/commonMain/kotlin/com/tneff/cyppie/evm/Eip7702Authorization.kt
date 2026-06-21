package com.tneff.cyppie.evm

import com.tneff.cyppie.evm.rlp.Rlp

/** Thrown when a 7702 authorization fails a check — the app must NOT owner-sign it (fail-closed). */
class AuthorizationVerificationException(message: String) : Exception(message)

/**
 * On-device verification + digest for the **EIP-7702 authorization** the backend returns on the FIRST enable of a
 * fresh follower (KAN-160, Pre-GA / GAP-B sibling). Signing a 7702 authorization delegates the EOA's code to the
 * tuple's `address` — a **separate full-authority signature** that [EnableUserOpVerifier] does NOT cover (its KAT
 * account was already 7702-upgraded, `initCode=0x`). A malicious delegate-target = total account takeover, so the
 * app MUST pin `address` to the canonical Kernel implementation and bind chainId BEFORE signing.
 *
 * 🔒 [KERNEL_V3_3_IMPLEMENTATION] is a **client-pinned constant** from the official ZeroDev/permissionless Kernel
 * v3.3 deployment (the same value `to7702KernelSmartAccount(version:"0.3.3").authorization.address` resolves to) —
 * NEVER taken from the backend payload. Reconcile the exact impl version with the backend before GA.
 */
object Eip7702Authorization {

    /** Canonical Kernel v3.3 implementation = the only permitted 7702 delegate-target. */
    const val KERNEL_V3_3_IMPLEMENTATION: String = "0xd6CEDDe84be40893d153Be9d467CD6aD37875b28"

    private const val MAGIC: Byte = 0x05 // EIP-7702 authorization magic

    /**
     * Verifies the authorization tuple ([chainId], [address], [nonce]) against [expectedChainId] and the pinned
     * delegate-target, then returns the 32-byte digest to owner-sign. Throws [AuthorizationVerificationException]
     * on any mismatch (do NOT sign).
     */
    fun verify(chainId: Long, address: String, nonce: Long, expectedChainId: Long): ByteArray {
        if (!address.equals(KERNEL_V3_3_IMPLEMENTATION, ignoreCase = true)) {
            throw AuthorizationVerificationException("7702 delegate-target != pinned Kernel v3.3 implementation: $address")
        }
        if (chainId != expectedChainId) {
            throw AuthorizationVerificationException("7702 authorization chainId ($chainId) != expected ($expectedChainId)")
        }
        if (nonce < 0) throw AuthorizationVerificationException("7702 authorization nonce is negative")
        return authorizationDigest(chainId, address, nonce)
    }

    /** EIP-7702 authorization digest: `keccak256(0x05 ‖ rlp([chainId, address, nonce]))`. */
    fun authorizationDigest(chainId: Long, address: String, nonce: Long): ByteArray {
        val list = Rlp.list(
            Rlp.string(minimalUint(chainId)),
            Rlp.string(addressBytes(address)),
            Rlp.string(minimalUint(nonce)),
        )
        return Keccak.keccak256(byteArrayOf(MAGIC) + Rlp.encode(list))
    }

    /** Minimal big-endian encoding (RLP integer form): 0 → empty, no leading zero bytes. */
    private fun minimalUint(value: Long): ByteArray {
        require(value >= 0) { "negative uint" }
        if (value == 0L) return ByteArray(0)
        var v = value
        val out = ArrayDeque<Byte>()
        while (v > 0) { out.addFirst((v and 0xFF).toByte()); v = v ushr 8 }
        return out.toByteArray()
    }

    private fun addressBytes(address: String): ByteArray {
        val b = Hex.decodeOrNull(address.removePrefix("0x").removePrefix("0X"))
            ?: throw AuthorizationVerificationException("bad address hex: $address")
        if (b.size != 20) throw AuthorizationVerificationException("address must be 20 bytes: $address")
        return b
    }
}
