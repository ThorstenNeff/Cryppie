package com.tneff.cyppie.evm

/**
 * An unsigned EVM quantity (0 .. 2^256-1), stored as **minimal big-endian** bytes (no leading
 * zero; zero is the empty array). EVM scalars — nonce, value (wei), gas, fees, chainId — are all
 * encoded this way for RLP, and left-padded to 32 bytes for ABI.
 *
 * L2 performs no arithmetic on quantities (decimal↔wei conversion belongs to the UI/L3 layer);
 * a [Quantity] is purely a validated byte container, which keeps the tx encoder deterministic and
 * auditable (ADR-0014).
 */
class Quantity private constructor(private val magnitude: ByteArray) {

    init {
        require(magnitude.size <= 32) { "Quantity exceeds 256 bits" }
        require(magnitude.isEmpty() || magnitude[0].toInt() != 0) { "magnitude must be minimal (no leading zero)" }
    }

    /** Minimal big-endian bytes (empty for zero) — the RLP scalar form. */
    fun toMinimalBytes(): ByteArray = magnitude.copyOf()

    /** Big-endian, left zero-padded to 32 bytes — the ABI `uint256` form. */
    fun toBytes32(): ByteArray {
        val out = ByteArray(32)
        magnitude.copyInto(out, destinationOffset = 32 - magnitude.size)
        return out
    }

    /** `0x`-prefixed minimal hex (`0x0` for zero), per the JSON-RPC QUANTITY encoding. */
    fun toHex(): String = if (magnitude.isEmpty()) "0x0" else "0x" + Hex.encode(magnitude).trimStart('0').ifEmpty { "0" }

    val isZero: Boolean get() = magnitude.isEmpty()

    /** As a non-negative [Long]; throws [EvmException.InvalidQuantity] if it exceeds the Long range. */
    fun toLong(): Long {
        if (magnitude.size > 8) throw EvmException.InvalidQuantity("Quantity exceeds Long range")
        var v = 0L
        for (b in magnitude) v = (v shl 8) or (b.toLong() and 0xFF)
        if (v < 0) throw EvmException.InvalidQuantity("Quantity exceeds Long range")
        return v
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Quantity && magnitude.contentEquals(other.magnitude))

    override fun hashCode(): Int = magnitude.contentHashCode()

    override fun toString(): String = toHex()

    companion object {
        val ZERO: Quantity = Quantity(ByteArray(0))

        /** From a non-negative [Long]. */
        fun of(value: Long): Quantity {
            if (value < 0) throw EvmException.InvalidQuantity("Quantity must be non-negative")
            if (value == 0L) return ZERO
            var v = value
            val tmp = ByteArray(8)
            var i = 8
            while (v != 0L) {
                tmp[--i] = (v and 0xFF).toByte()
                v = v ushr 8
            }
            return Quantity(tmp.copyOfRange(i, 8))
        }

        /** From big-endian bytes; leading zeros are stripped. */
        fun ofBytes(bytes: ByteArray): Quantity {
            var start = 0
            while (start < bytes.size && bytes[start].toInt() == 0) start++
            return Quantity(bytes.copyOfRange(start, bytes.size))
        }

        /** From a hex string (optionally `0x`-prefixed); odd length is left-padded (e.g. `0x1`). */
        fun ofHex(hex: String): Quantity {
            var s = if (hex.startsWith("0x") || hex.startsWith("0X")) hex.substring(2) else hex
            if (s.length % 2 != 0) s = "0$s"
            val bytes = Hex.decodeOrNull(s)
                ?: throw EvmException.InvalidQuantity("Invalid hex quantity")
            return ofBytes(bytes)
        }
    }
}
