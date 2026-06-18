package com.tneff.cyppie.wallet

import com.tneff.cyppie.evm.EvmAddress

/**
 * A recoverable secp256k1 ECDSA signature over a 32-byte message hash.
 *
 * [r] and [s] are 32 bytes each; [s] is low-S normalized (EIP-2). [recId] is the 0..3 recovery
 * id (yParity). L2 (ADR-0014) computes the EVM `v` from [recId] (`v = recId + 35 + 2*chainId`
 * for EIP-155, or `27 + recId` for `personal_sign`); L1 stays chain-agnostic.
 */
class RecoverableSignature(
    val r: ByteArray,
    val s: ByteArray,
    val recId: Int,
) {
    init {
        require(r.size == 32) { "r must be 32 bytes" }
        require(s.size == 32) { "s must be 32 bytes" }
        require(recId in 0..3) { "recId must be in 0..3" }
    }

    /** Concatenated `r || s` (64 bytes). */
    fun toCompact(): ByteArray = r + s

    override fun equals(other: Any?): Boolean =
        this === other || (other is RecoverableSignature &&
            recId == other.recId && r.contentEquals(other.r) && s.contentEquals(other.s))

    override fun hashCode(): Int = (r.contentHashCode() * 31 + s.contentHashCode()) * 31 + recId

    /** Recovers the signer's EIP-55 address from this signature over [messageHash] (32 bytes). */
    fun recoverAddress(messageHash: ByteArray): EvmAddress =
        EvmAddress.fromPublicKey(EvmCrypto.recoverPublicKey(messageHash, this))
}
