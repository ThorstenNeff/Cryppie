package com.tneff.cyppie.wallet

/**
 * BIP-44 derivation for EVM chains. MVP path is fixed to `m/44'/60'/0'/0/i`
 * (PO guardrail #4); multi-account is the trailing address index `i`.
 * Coin type 60 = Ethereum (shared by all EVM chains incl. Base).
 */
object Bip44 {

    const val EVM_COIN_TYPE: Int = 60

    /** `m/44'/60'/0'/0/index` for the given non-negative account [index]. */
    fun evmPath(index: Int): String {
        if (index < 0) {
            throw WalletKeyException.InvalidDerivationIndex("Account index must be >= 0, was $index")
        }
        return "m/44'/$EVM_COIN_TYPE'/0'/0/$index"
    }
}
