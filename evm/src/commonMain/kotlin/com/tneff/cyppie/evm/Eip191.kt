package com.tneff.cyppie.evm

/**
 * EIP-191 `personal_sign` digest (version `0x45`):
 * `keccak256(0x19 ‖ "Ethereum Signed Message:\n" ‖ len(message) ‖ message)`.
 *
 * The single, auditable home of the prefixed-message digest (KAN-141): the WalletConnect `personal_sign`
 * path and the SIWE signer both call this, so the EIP-191 prefix and length encoding live in exactly one
 * reviewed place (no duplicate, no masked-`0x19` drift).
 *
 * ⚠️ This is the **prefixed** signing path (human-readable messages / SIWE login). It is deliberately
 * **separate** from the **raw** AA digest path (`:wallet`'s `UserOpSigner`, which signs a userOp/7702 hash
 * with no prefix). Never route a userOp/AA hash through here, and never personal-sign-prefix an AA digest.
 *
 * Web-safe (`:evm`, keccak only — no secp256k1): computing the digest is keyless; the actual signature
 * still requires `:wallet`.
 */
object Eip191 {

    /**
     * The EIP-191 personal-sign digest of [message]. The length embedded in the prefix is the message's
     * **byte** length (not character count), per the spec; [message] is hashed verbatim after the prefix.
     */
    fun personalSignDigest(message: ByteArray): ByteArray {
        val prefix = byteArrayOf(0x19) + "Ethereum Signed Message:\n${message.size}".encodeToByteArray()
        return Keccak.keccak256(prefix + message)
    }
}
