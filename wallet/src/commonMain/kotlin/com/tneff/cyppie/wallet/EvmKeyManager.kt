package com.tneff.cyppie.wallet

import com.tneff.cyppie.evm.EvmAddress

/**
 * L1 Key/Account layer (KAN-56) — the clean, synchronous API consumed by L2 (tx signing,
 * ADR-0014), L3 (RPC) and L4 (WalletConnect).
 *
 * Responsibilities:
 *  - BIP-44 HD derivation of EVM accounts along the fixed `m/44'/60'/0'/0/i` path.
 *  - Multi-account via the index `i` ([deriveAccount]).
 *  - EIP-55 addresses ([EvmAddress]).
 *  - On-demand signing ([sign]) gated by [SeedSource] (unlock→sign, ADR-0009).
 *
 * Security: private keys are derived only inside the [SeedSource.withSeed] scope, used, and then
 * zeroized. No private key is ever returned, stringified, persisted, or logged (PO guardrail #3).
 * The only outward surface is the address and `sign(...)`.
 *
 * All methods are pure/synchronous; the caller owns threading (PO guardrail #6).
 */
class EvmKeyManager(private val seedSource: SeedSource) {

    /** Derives the public [EvmAccount] (index, EIP-55 address, path) for account [index]. */
    fun deriveAccount(index: Int): EvmAccount {
        val path = Bip44.evmPath(index)
        val address = withPrivateKey(path) { priv -> EvmAddress.fromPublicKey(EvmCrypto.publicKey(priv)) }
        return EvmAccount(index = index, address = address, path = path)
    }

    /** Convenience: the EIP-55 address for account [index]. */
    fun deriveAddress(index: Int): EvmAddress = deriveAccount(index).address

    /**
     * Signs a pre-computed 32-byte message [hash] with account [index]'s key (e.g. the keccak256
     * of an RLP-encoded EIP-1559 tx, or the `personal_sign` digest). Returns a recoverable,
     * low-S signature; L2 derives the chain-specific `v`.
     */
    fun sign(index: Int, hash: ByteArray): RecoverableSignature {
        if (hash.size != 32) {
            throw WalletKeyException.InvalidSigningInput("Message hash must be 32 bytes, was ${hash.size}")
        }
        return withPrivateKey(Bip44.evmPath(index)) { priv -> EvmCrypto.sign(priv, hash) }
    }

    /**
     * Derives the private key at [path] inside the seed scope, runs [block], and zeroizes the key
     * before returning. The key never leaves this function.
     */
    private inline fun <R> withPrivateKey(path: String, crossinline block: (ByteArray) -> R): R =
        seedSource.withSeed { seed ->
            val priv = EvmCrypto.derivePrivateKey(seed, path)
            try {
                block(priv)
            } finally {
                priv.fill(0)
            }
        }
}
