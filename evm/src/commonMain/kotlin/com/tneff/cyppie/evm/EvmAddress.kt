package com.tneff.cyppie.evm

/**
 * A 20-byte EVM account address.
 *
 * The external string form is **always** the EIP-55 mixed-case checksum with a `0x` prefix
 * (PO guardrail #2): [value] and [toString]. Equality is on the raw bytes, so two instances
 * parsed from differently-cased inputs compare equal.
 */
class EvmAddress private constructor(private val raw: ByteArray) {

    init {
        require(raw.size == LENGTH) { "EVM address must be $LENGTH bytes" }
    }

    /** Defensive copy of the 20 address bytes. */
    val bytes: ByteArray get() = raw.copyOf()

    /** EIP-55 checksummed, `0x`-prefixed form — the canonical external representation. */
    val value: String by lazy { toEip55(Hex.encode(raw)) }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        this === other || (other is EvmAddress && raw.contentEquals(other.raw))

    override fun hashCode(): Int = raw.contentHashCode()

    companion object {
        const val LENGTH: Int = 20

        /** Wraps 20 raw address bytes. */
        fun fromBytes(bytes: ByteArray): EvmAddress {
            if (bytes.size != LENGTH) {
                throw EvmException.InvalidAddress("Expected $LENGTH bytes, got ${bytes.size}")
            }
            return EvmAddress(bytes.copyOf())
        }

        /**
         * Derives the address from an uncompressed secp256k1 public key
         * (`0x04 || X || Y`, 65 bytes): `keccak256(X || Y)[12..]`.
         */
        fun fromPublicKey(uncompressedPublicKey: ByteArray): EvmAddress {
            val key = when (uncompressedPublicKey.size) {
                65 -> uncompressedPublicKey.copyOfRange(1, 65) // drop 0x04 prefix
                64 -> uncompressedPublicKey
                else -> throw EvmException.InvalidAddress(
                    "Expected 64/65-byte uncompressed public key, got ${uncompressedPublicKey.size}",
                )
            }
            val hash = Keccak.keccak256(key)
            return EvmAddress(hash.copyOfRange(hash.size - LENGTH, hash.size))
        }

        /**
         * Parses a `0x`-prefixed hex address. If the input is mixed-case it must pass its
         * EIP-55 checksum; all-lower / all-upper input is accepted without a checksum check.
         */
        fun parse(text: String): EvmAddress {
            val t = text.trim()
            if (!t.startsWith("0x") && !t.startsWith("0X")) {
                throw EvmException.InvalidAddress("Address must be 0x-prefixed")
            }
            val body = t.substring(2)
            if (body.length != LENGTH * 2 || body.any { hexNibble(it) < 0 }) {
                throw EvmException.InvalidAddress("Address must be 40 hex digits")
            }
            val hasUpper = body.any { it in 'A'..'F' }
            val hasLower = body.any { it in 'a'..'f' }
            if (hasUpper && hasLower && toEip55(body.lowercase()) != "0x$body") {
                throw EvmException.InvalidAddress("EIP-55 checksum mismatch")
            }
            return EvmAddress(Hex.decodeOrNull(body)!!)
        }

        /** True iff [text] would parse (format + EIP-55 when mixed-case). */
        fun isValid(text: String): Boolean =
            runCatching { parse(text) }.isSuccess

        /** Applies the EIP-55 checksum to a 40-char lowercase hex address, returning `0x…`. */
        private fun toEip55(lowerHex: String): String {
            val hashHex = Hex.encode(Keccak.keccak256(lowerHex.encodeToByteArray()))
            val sb = StringBuilder(2 + lowerHex.length)
            sb.append("0x")
            for (i in lowerHex.indices) {
                val c = lowerHex[i]
                if (c in 'a'..'f' && hexNibble(hashHex[i]) >= 8) sb.append(c.uppercaseChar()) else sb.append(c)
            }
            return sb.toString()
        }

        private fun hexNibble(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
    }
}
