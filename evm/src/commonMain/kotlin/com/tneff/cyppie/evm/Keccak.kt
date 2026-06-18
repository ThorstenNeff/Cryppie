package com.tneff.cyppie.evm

import org.kotlincrypto.hash.sha3.Keccak256

/**
 * keccak-256 (the Ethereum variant — *not* SHA3-256, which differs in padding).
 *
 * Backed by KotlinCrypto `sha3`, which is multiplatform incl. js/wasm, so this lives in
 * `commonMain` with no `expect`/`actual` — unlike secp256k1 it needs no native binding (ADR-0016).
 */
object Keccak {
    /** keccak-256 digest of [input] (32 bytes). */
    fun keccak256(input: ByteArray): ByteArray = Keccak256().digest(input)
}
