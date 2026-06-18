package com.tneff.cyppie.storage

import com.tneff.cyppie.wallet.SeedSource

/**
 * A [SeedSource] over a decrypted seed that **can be zeroized** (M1, ADR-0005 §5.3): the app holds
 * it for the unlocked session and calls [close] on lock to wipe the seed from memory. After [close],
 * [withSeed] throws. (A deeper, interface-wide zeroization seam is tracked as KAN-71.)
 */
class SecureSeedSource internal constructor(private val seed: ByteArray) : SeedSource, AutoCloseable {

    private var closed = false

    override fun <R> withSeed(block: (ByteArray) -> R): R {
        check(!closed) { "seed source is closed" }
        return block(seed)
    }

    /** Zeroizes the held seed; subsequent [withSeed] calls fail. Idempotent. */
    override fun close() {
        seed.fill(0)
        closed = true
    }
}
