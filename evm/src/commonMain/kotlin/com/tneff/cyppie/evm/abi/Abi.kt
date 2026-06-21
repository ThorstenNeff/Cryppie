package com.tneff.cyppie.evm.abi

import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity

/**
 * Minimal ABI **encoder** for the nested-dynamic shapes `verifyEnableUserOp` re-encodes (the SmartSessions
 * `enableSessions(Session[])` argument — KAN-154/KAN-159). Supports the head/tail layout for tuples and dynamic
 * arrays of `address`/`bytes4`/`bytes32`/`bool`/`bytes`/`string`. Encoding (not decoding) is deliberate: the
 * verifier rebuilds the EXPECTED calldata from client-pinned session fields and byte-compares it to the backend
 * userOp — so there is no decoder-incompleteness gap a malicious payload could hide bytes in.
 */
object Abi {

    /** An ABI value. [isDynamic] decides head (offset) vs inline; [encoding] is the tail (dynamic) or inline words (static). */
    sealed interface Value {
        val isDynamic: Boolean
        fun encoding(): ByteArray
    }

    private class Static(val words: ByteArray) : Value {
        override val isDynamic = false
        override fun encoding() = words
    }

    private class Dynamic(val tail: ByteArray) : Value {
        override val isDynamic = true
        override fun encoding() = tail
    }

    fun address(hex: String): Value = Static(leftPad32(decode(hex, 20)))
    fun bytes32(hex: String): Value = Static(decode(hex, 32))
    fun bytes4(hex: String): Value = Static(rightPad32(decode(hex, 4)))
    fun bool(v: Boolean): Value = Static(ByteArray(32).also { if (v) it[31] = 1 })
    fun uint(v: Quantity): Value = Static(v.toBytes32())
    fun bytes(hex: String): Value = encodeDynamicBytes(decode(hex))
    fun string(v: String): Value = encodeDynamicBytes(v.encodeToByteArray())

    /** A `T[]` dynamic array: `length ‖ tupleBody(elems)`. */
    fun array(elems: List<Value>): Value = Dynamic(Quantity.of(elems.size.toLong()).toBytes32() + tupleBody(elems))

    /** A struct: dynamic iff any field is dynamic; encoding is the head/tail body of its fields. */
    fun tuple(fields: List<Value>): Value =
        if (fields.any { it.isDynamic }) Dynamic(tupleBody(fields)) else Static(tupleBody(fields))

    /** Top-level `abi.encode(args...)` — the outer tuple body (no selector). */
    fun encode(vararg args: Value): ByteArray = tupleBody(args.toList())

    /** `selector ‖ abi.encode(args...)`. [selector] is a `0x…`-prefixed 4-byte hex. */
    fun encodeWithSelector(selector: String, vararg args: Value): ByteArray = decode(selector, 4) + tupleBody(args.toList())

    // ── head/tail assembly ──
    private fun tupleBody(fields: List<Value>): ByteArray {
        val statics = fields.map { if (it.isDynamic) null else it.encoding() }
        var headLen = 0
        for (i in fields.indices) headLen += if (fields[i].isDynamic) 32 else statics[i]!!.size
        val heads = ArrayList<ByteArray>(fields.size)
        val tails = ArrayList<ByteArray>()
        var off = headLen
        for (i in fields.indices) {
            if (fields[i].isDynamic) {
                heads.add(Quantity.of(off.toLong()).toBytes32())
                val tail = fields[i].encoding()
                tails.add(tail)
                off += tail.size
            } else {
                heads.add(statics[i]!!)
            }
        }
        val out = ArrayList<ByteArray>(heads.size + tails.size)
        out.addAll(heads); out.addAll(tails)
        return concat(out)
    }

    private fun encodeDynamicBytes(b: ByteArray): Value {
        val padded = if (b.size % 32 == 0) b else b + ByteArray(32 - b.size % 32)
        return Dynamic(Quantity.of(b.size.toLong()).toBytes32() + padded)
    }

    // ── byte helpers ──
    private fun decode(hex: String): ByteArray =
        Hex.decodeOrNull(hex.removePrefix("0x").removePrefix("0X")) ?: throw IllegalArgumentException("bad hex: $hex")

    private fun decode(hex: String, expectedLen: Int): ByteArray {
        val b = decode(hex)
        require(b.size == expectedLen) { "expected $expectedLen bytes, got ${b.size}: $hex" }
        return b
    }

    private fun leftPad32(b: ByteArray): ByteArray = ByteArray(32).also { b.copyInto(it, 32 - b.size) }
    private fun rightPad32(b: ByteArray): ByteArray = ByteArray(32).also { b.copyInto(it, 0) }

    private fun concat(parts: List<ByteArray>): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var p = 0
        for (part in parts) { part.copyInto(out, p); p += part.size }
        return out
    }
}
