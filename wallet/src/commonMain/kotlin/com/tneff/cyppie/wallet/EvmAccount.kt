package com.tneff.cyppie.wallet

import com.tneff.cyppie.evm.EvmAddress

/**
 * A derived EVM account — public information only. Holds no private key (PO guardrail #3):
 * signing goes through [EvmKeyManager.sign], which derives the key on demand and zeroizes it.
 */
data class EvmAccount(
    /** Multi-account index `i` in `m/44'/60'/0'/0/i`. */
    val index: Int,
    /** EIP-55 checksummed address. */
    val address: EvmAddress,
    /** Full BIP-44 derivation path. */
    val path: String,
)
