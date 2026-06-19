package com.tneff.cyppie.auth

import com.tneff.cyppie.evm.Eip191
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.EvmKeyManager
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.wallet.WalletKeyException

/**
 * The [SiweSigner] impl (KAN-141) over the shared EIP-191 path ([Eip191], KAN-142 — the same prefixed
 * digest WalletConnect's `personal_sign` uses; distinct from the raw `UserOpSigner` for AA digests).
 * Signs the canonical SIWE message as the SCA/SIWE-identity account (index 0), returning the `0x`-65-byte
 * `r‖s‖v` (v = recId + 27). 🔒 Owner-bind (no-blind): the signing account must derive to [owner]; the fresh
 * [SeedSource] is zeroized immediately after signing, even on failure (mirrors Send/WC/AaSigner).
 */
class Eip191SiweSigner(private val accountIndex: Int = 0) : SiweSigner {

    override fun sign(message: SiweMessage, owner: EvmAddress, seedSource: SeedSource): String {
        val keyManager = EvmKeyManager(seedSource)
        val sig = try {
            if (keyManager.deriveAddress(accountIndex) != owner) {
                throw WalletKeyException.SignerMismatch("SIWE signer #$accountIndex does not derive to $owner")
            }
            keyManager.sign(accountIndex, Eip191.personalSignDigest(message.canonical().encodeToByteArray()))
        } finally {
            (seedSource as? AutoCloseable)?.close()
        }
        return "0x" + Hex.encode(sig.r + sig.s + byteArrayOf((27 + sig.recId).toByte()))
    }
}
