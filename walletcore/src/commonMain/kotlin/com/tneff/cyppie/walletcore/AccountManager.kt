package com.tneff.cyppie.walletcore

import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.wallet.EvmKeyManager

/**
 * Derives and lists the wallet's EVM accounts (read-side — addresses only; no private keys leave
 * L1). Multi-account is the BIP-44 index `i`. Derivation runs through [EvmKeyManager], so a usable
 * [EvmKeyManager] (unlocked [com.tneff.cyppie.wallet.SeedSource]) is required.
 */
class AccountManager(private val keyManager: EvmKeyManager) {

    /** The account (index, EIP-55 address, path) at [index]. */
    fun account(index: Int): EvmAccount = keyManager.deriveAccount(index)

    /** The first [count] accounts (indices `0 until count`). */
    fun accounts(count: Int): List<EvmAccount> {
        require(count > 0) { "count must be > 0, was $count" }
        return (0 until count).map { keyManager.deriveAccount(it) }
    }
}
