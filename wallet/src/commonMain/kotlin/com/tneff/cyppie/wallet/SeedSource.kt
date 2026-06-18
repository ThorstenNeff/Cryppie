package com.tneff.cyppie.wallet

/**
 * The unlock→sign gate (ADR-0009). Supplies the decrypted 64-byte BIP-39 seed to a short-lived
 * block and is expected to throw [WalletKeyException.WalletLocked] when the wallet is locked.
 *
 * L1 never persists the seed or any derived key: the storage/biometric-unlock layer owns the
 * seed's lifecycle and decides when it is available. The (possibly async) unlock happens upstream;
 * the block itself is synchronous and CPU-bound (PO guardrail #6).
 */
interface SeedSource {
    /** Runs [block] with the decrypted seed, or throws [WalletKeyException.WalletLocked]. */
    fun <R> withSeed(block: (seed: ByteArray) -> R): R

    companion object {
        /**
         * A non-persistent in-memory source over an already-decrypted [seed]. Intended for tests
         * and for callers that have just unlocked and hold the seed transiently. Does not copy or
         * zeroize [seed] — the caller owns that.
         */
        fun ofSeed(seed: ByteArray): SeedSource = object : SeedSource {
            override fun <R> withSeed(block: (ByteArray) -> R): R = block(seed)
        }
    }
}
