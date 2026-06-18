package com.tneff.cyppie.evm.rlp

import com.tneff.cyppie.evm.EvmException

/**
 * Recursive-Length Prefix (RLP) item — either a byte string or a list of items.
 * The two cases are the whole RLP data model (Ethereum Yellow Paper, Appendix B).
 */
sealed interface RlpItem {
    /** A byte string (atomic). */
    class Str(val bytes: ByteArray) : RlpItem {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Str && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** An ordered list of items. */
    class Lst(val items: List<RlpItem>) : RlpItem {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Lst && items == other.items)
        override fun hashCode(): Int = items.hashCode()
    }
}

/** Minimal, deterministic RLP encoder/decoder (ADR-0014). */
object Rlp {

    fun string(bytes: ByteArray): RlpItem.Str = RlpItem.Str(bytes)
    fun list(vararg items: RlpItem): RlpItem.Lst = RlpItem.Lst(items.toList())
    fun list(items: List<RlpItem>): RlpItem.Lst = RlpItem.Lst(items)

    fun encode(item: RlpItem): ByteArray = when (item) {
        is RlpItem.Str -> encodeString(item.bytes)
        is RlpItem.Lst -> {
            var payload = ByteArray(0)
            for (child in item.items) payload += encode(child)
            encodeLength(payload.size, offset = 0xc0) + payload
        }
    }

    private fun encodeString(bytes: ByteArray): ByteArray =
        if (bytes.size == 1 && (bytes[0].toInt() and 0xFF) < 0x80) {
            bytes // single byte below 0x80 is its own encoding
        } else {
            encodeLength(bytes.size, offset = 0x80) + bytes
        }

    private fun encodeLength(length: Int, offset: Int): ByteArray =
        if (length < 56) {
            byteArrayOf((offset + length).toByte())
        } else {
            val lenBytes = toBigEndian(length)
            byteArrayOf((offset + 55 + lenBytes.size).toByte()) + lenBytes
        }

    private fun toBigEndian(value: Int): ByteArray {
        var v = value
        val tmp = ByteArray(4)
        var i = 4
        while (v != 0) {
            tmp[--i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return tmp.copyOfRange(i, 4)
    }

    // ---- decoding ----

    /** Decodes exactly one RLP item, requiring it to span the whole [input]. */
    fun decode(input: ByteArray): RlpItem {
        val (item, consumed) = decodeItem(input, 0)
        if (consumed != input.size) {
            throw EvmException.InvalidRlp("Trailing bytes after RLP item")
        }
        return item
    }

    private fun decodeItem(input: ByteArray, pos: Int): Pair<RlpItem, Int> {
        if (pos >= input.size) throw EvmException.InvalidRlp("RLP: unexpected end")
        val prefix = input[pos].toInt() and 0xFF
        return when {
            prefix < 0x80 -> RlpItem.Str(byteArrayOf(input[pos])) to 1
            prefix < 0xb8 -> {
                val len = prefix - 0x80
                val start = pos + 1
                requireRange(input, start, len)
                RlpItem.Str(input.copyOfRange(start, start + len)) to (1 + len)
            }
            prefix < 0xc0 -> {
                val lenOfLen = prefix - 0xb7
                val len = readLength(input, pos + 1, lenOfLen)
                val start = pos + 1 + lenOfLen
                requireRange(input, start, len)
                RlpItem.Str(input.copyOfRange(start, start + len)) to (1 + lenOfLen + len)
            }
            prefix < 0xf8 -> {
                val len = prefix - 0xc0
                decodeList(input, pos + 1, len, headerSize = 1)
            }
            else -> {
                val lenOfLen = prefix - 0xf7
                val len = readLength(input, pos + 1, lenOfLen)
                decodeList(input, pos + 1 + lenOfLen, len, headerSize = 1 + lenOfLen)
            }
        }
    }

    private fun decodeList(input: ByteArray, contentStart: Int, contentLen: Int, headerSize: Int): Pair<RlpItem, Int> {
        requireRange(input, contentStart, contentLen)
        val items = ArrayList<RlpItem>()
        var p = contentStart
        val end = contentStart + contentLen
        while (p < end) {
            val (child, consumed) = decodeItem(input, p)
            items.add(child)
            p += consumed
        }
        if (p != end) throw EvmException.InvalidRlp("RLP: list element overran")
        return RlpItem.Lst(items) to (headerSize + contentLen)
    }

    private fun readLength(input: ByteArray, pos: Int, lenOfLen: Int): Int {
        requireRange(input, pos, lenOfLen)
        var len = 0
        for (i in 0 until lenOfLen) len = (len shl 8) or (input[pos + i].toInt() and 0xFF)
        return len
    }

    private fun requireRange(input: ByteArray, start: Int, len: Int) {
        if (start < 0 || len < 0 || start + len > input.size) {
            throw EvmException.InvalidRlp("RLP: length out of bounds")
        }
    }
}
