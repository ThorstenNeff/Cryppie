package com.tneff.cyppie.evm

/**
 * An unsigned EVM quantity (0 .. 2^256-1), stored as **minimal big-endian** bytes (no leading
 * zero; zero is the empty array). EVM scalars — nonce, value (wei), gas, fees, chainId — are all
 * encoded this way for RLP, and left-padded to 32 bytes for ABI.
 *
 * The L2 tx encoder performs no arithmetic (decimal↔wei conversion belongs to the UI). The minimal
 * unsigned 256-bit [plus]/[times]/[compareTo] below exist for the **send pipeline's** balance check
 * (`value + gasLimit * maxFee`, KAN-91) — exact, overflow-checked (a result > 2^256-1 throws).
 */
class Quantity private constructor(private val magnitude: ByteArray) : Comparable<Quantity> {

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

    /** Unsigned big-endian comparison (both magnitudes are minimal, so length orders first). */
    override fun compareTo(other: Quantity): Int {
        if (magnitude.size != other.magnitude.size) return magnitude.size - other.magnitude.size
        for (i in magnitude.indices) {
            val d = (magnitude[i].toInt() and 0xFF) - (other.magnitude[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return 0
    }

    /** Exact 256-bit addition; throws on overflow (a result > 2^256-1). */
    operator fun plus(other: Quantity): Quantity {
        val a = magnitude
        val b = other.magnitude
        val n = maxOf(a.size, b.size) + 1
        val out = ByteArray(n)
        var carry = 0
        for (i in 0 until n) {
            val av = if (i < a.size) a[a.size - 1 - i].toInt() and 0xFF else 0
            val bv = if (i < b.size) b[b.size - 1 - i].toInt() and 0xFF else 0
            val s = av + bv + carry
            out[n - 1 - i] = (s and 0xFF).toByte()
            carry = s shr 8
        }
        return ofBytes(out)
    }

    /** Exact 256-bit multiplication; throws on overflow (a result > 2^256-1). */
    operator fun times(other: Quantity): Quantity {
        val a = magnitude
        val b = other.magnitude
        if (a.isEmpty() || b.isEmpty()) return ZERO
        val acc = IntArray(a.size + b.size)
        for (i in a.indices) {
            val av = a[a.size - 1 - i].toInt() and 0xFF
            var carry = 0
            for (j in b.indices) {
                val bv = b[b.size - 1 - j].toInt() and 0xFF
                val s = acc[i + j] + av * bv + carry
                acc[i + j] = s and 0xFF
                carry = s shr 8
            }
            acc[i + b.size] += carry
        }
        val bytes = ByteArray(acc.size)
        for (k in acc.indices) bytes[acc.size - 1 - k] = (acc[k] and 0xFF).toByte()
        return ofBytes(bytes)
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
