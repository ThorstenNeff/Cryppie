package com.tneff.cyppie.evm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Fail-closed signal for malformed / out-of-range EIP-712 typed data (KAN-143). Neutral to `:evm` so the
 *  digest builder is shared by WalletConnect's `eth_signTypedData_v4` AND the AA enable-recompute. */
class Eip712Exception(message: String) : RuntimeException(message)

/**
 * EIP-712 (`eth_signTypedData_v4`) digest builder. Produces the 32-byte hash to sign:
 * `keccak256(0x1901 ‖ domainSeparator ‖ hashStruct(primaryType, message))`, where
 * `hashStruct(s) = keccak256(typeHash(s) ‖ encodeData(s))` and `typeHash = keccak256(encodeType)`.
 *
 * Lifted to `:evm` (KAN-143, like Eip191/KAN-142) so it is the **single auditable EIP-712 source** shared by
 * WalletConnect's typed-data signing and the AA **enable-recompute** (on-device verification of the
 * Smart-Sessions enable digest before signing — approach C). Web-safe: pure keccak + runtime JSON, no
 * secp256k1.
 *
 * Security-sensitive + encoding-heavy (ADR-0014 ethos) — validated against the canonical EIP-712 "Mail"
 * example; authoritative recover-to-signer vectors live in `:walletconnect` (WC merge gate, KAN-63).
 * Addresses are decoded as raw 20 bytes (no EIP-55 enforcement — dapp typed-data is often un-checksummed).
 */
object Eip712 {

    private val json = Json { ignoreUnknownKeys = true }

    /** The 32-byte EIP-712 digest for a `eth_signTypedData_v4` payload [typedDataJson]. */
    fun encode(typedDataJson: String): ByteArray = try {
        val root = json.parseToJsonElement(typedDataJson).jsonObject
        val types = root.getValue("types").jsonObject
        val primaryType = root.getValue("primaryType").jsonPrimitive.content
        val domain = root.getValue("domain").jsonObject
        val message = root.getValue("message").jsonObject
        val domainSeparator = hashStruct("EIP712Domain", domain, types)
        val messageHash = hashStruct(primaryType, message, types)
        Keccak.keccak256(byteArrayOf(0x19, 0x01) + domainSeparator + messageHash)
    } catch (e: Eip712Exception) {
        throw e
    } catch (e: NoSuchElementException) {
        throw Eip712Exception("Malformed EIP-712 typed data (missing field)")
    } catch (e: IllegalArgumentException) {
        // JsonObject/JsonArray/primitive cast failures, bad decimals, oversize values, etc. → fail-closed.
        throw Eip712Exception("Malformed EIP-712 typed data: ${e.message}")
    }

    private fun hashStruct(type: String, data: JsonObject, types: JsonObject): ByteArray =
        Keccak.keccak256(typeHash(type, types) + encodeData(type, data, types))

    private fun typeHash(type: String, types: JsonObject): ByteArray =
        Keccak.keccak256(encodeType(type, types).encodeToByteArray())

    /** `Primary(field...)Dep1(...)Dep2(...)` — primary first, referenced struct types sorted. */
    private fun encodeType(primaryType: String, types: JsonObject): String {
        val deps = mutableSetOf<String>()
        collectDeps(primaryType, types, deps)
        deps.remove(primaryType)
        val ordered = listOf(primaryType) + deps.sorted()
        return ordered.joinToString("") { t ->
            val fields = types.getValue(t).jsonArray.joinToString(",") { f ->
                val o = f.jsonObject
                "${o.getValue("type").jsonPrimitive.content} ${o.getValue("name").jsonPrimitive.content}"
            }
            "$t($fields)"
        }
    }

    private fun collectDeps(type: String, types: JsonObject, acc: MutableSet<String>) {
        if (type in acc) return
        val fields = (types[type] as? JsonArray) ?: return
        acc.add(type)
        for (f in fields) {
            val base = baseType(f.jsonObject.getValue("type").jsonPrimitive.content)
            if (types[base] != null) collectDeps(base, types, acc)
        }
    }

    private fun baseType(type: String): String =
        type.indexOf('[').let { if (it >= 0) type.substring(0, it) else type }

    private fun encodeData(type: String, data: JsonObject, types: JsonObject): ByteArray {
        var out = ByteArray(0)
        for (f in types.getValue(type).jsonArray) {
            val o = f.jsonObject
            val name = o.getValue("name").jsonPrimitive.content
            val fieldType = o.getValue("type").jsonPrimitive.content
            out += encodeValue(fieldType, data.getValue(name), types)
        }
        return out
    }

    private fun encodeValue(type: String, value: JsonElement, types: JsonObject): ByteArray {
        if (type.endsWith("]")) { // array: keccak of concatenated element encodings
            val open = type.lastIndexOf('[')
            val elementType = type.substring(0, open)
            val fixedSize = type.substring(open + 1, type.length - 1) // "" for dynamic [], "n" for [n]
            val elements = value.jsonArray
            if (fixedSize.isNotEmpty() && elements.size != fixedSize.toInt()) {
                throw Eip712Exception("Array $type expects $fixedSize elements, got ${elements.size}")
            }
            var enc = ByteArray(0)
            for (e in elements) enc += encodeValue(elementType, e, types)
            return Keccak.keccak256(enc)
        }
        if (types[type] != null) return hashStruct(type, value.jsonObject, types) // nested struct
        return when {
            type == "string" -> Keccak.keccak256(value.jsonPrimitive.content.encodeToByteArray())
            type == "bytes" -> Keccak.keccak256(decodeHexOrThrow(value.jsonPrimitive.content))
            type == "bool" -> ByteArray(32).also { it[31] = if (value.jsonPrimitive.boolean) 1 else 0 }
            type == "address" -> leftPad32(decodeAddress(value.jsonPrimitive.content))
            type.startsWith("uint") -> uintTo32(type, value.jsonPrimitive.content)
            type.startsWith("int") -> intTo32(type, value.jsonPrimitive.content)
            type.startsWith("bytes") -> rightPad32(decodeHexOrThrow(value.jsonPrimitive.content))
            else -> throw Eip712Exception("Unsupported EIP-712 type: $type")
        }
    }

    private fun decodeHexOrThrow(hex: String): ByteArray =
        Hex.decodeOrNull(hex) ?: throw Eip712Exception("Invalid hex value: $hex")

    private fun decodeAddress(hex: String): ByteArray {
        val bytes = Hex.decodeOrNull(hex) ?: throw Eip712Exception("Bad address: $hex")
        if (bytes.size != 20) throw Eip712Exception("Address must be 20 bytes")
        return bytes
    }

    /** `uintN` (N=8..256, default 256): magnitude must fit N bits, else fail-closed. */
    private fun uintTo32(type: String, value: String): ByteArray {
        val bits = bitWidth(type, "uint")
        val bytes = if (value.startsWith("0x") || value.startsWith("0X")) Quantity.ofHex(value).toBytes32() else decimalTo32(value)
        if (highBytesNonZero(bytes, bits)) throw Eip712Exception("$type value exceeds $bits bits")
        return bytes
    }

    /** `intN`: rejects hex-negative; checks the signed value fits N bits; emits two's complement. */
    private fun intTo32(type: String, value: String): ByteArray {
        val bits = bitWidth(type, "int")
        if (value.startsWith("-0x") || value.startsWith("-0X")) {
            throw Eip712Exception("negative hex not allowed for $type")
        }
        if (!value.startsWith("-")) {
            val bytes = if (value.startsWith("0x") || value.startsWith("0X")) Quantity.ofHex(value).toBytes32() else decimalTo32(value)
            // positive: must fit N-1 bits (sign bit stays 0)
            if (highBytesNonZero(bytes, bits) || (bits < 256 && signBitSet(bytes, bits))) {
                throw Eip712Exception("$type value out of range")
            }
            return bytes
        }
        val magnitude = decimalTo32(value.substring(1)) // |value|; must be ≤ 2^(N-1)
        if (highBytesNonZero(magnitude, bits) || (bits < 256 && signBitSet(magnitude, bits) && !isExactlyMinInt(magnitude, bits))) {
            throw Eip712Exception("$type value out of range")
        }
        for (i in magnitude.indices) magnitude[i] = magnitude[i].toInt().inv().toByte()
        var carry = 1
        for (i in 31 downTo 0) {
            val v = (magnitude[i].toInt() and 0xFF) + carry
            magnitude[i] = (v and 0xFF).toByte()
            carry = v shr 8
        }
        return magnitude
    }

    /** Parses the bit width from a `uintN`/`intN` type (default 256); validates 8..256, multiple of 8. */
    private fun bitWidth(type: String, prefix: String): Int {
        val suffix = type.removePrefix(prefix)
        val bits = if (suffix.isEmpty()) 256 else suffix.toIntOrNull()
            ?: throw Eip712Exception("Unsupported type: $type")
        if (bits !in 8..256 || bits % 8 != 0) throw Eip712Exception("Invalid bit width: $type")
        return bits
    }

    /** True if any byte above the low N/8 bytes of [bytes32] is non-zero (value exceeds N bits). */
    private fun highBytesNonZero(bytes32: ByteArray, bits: Int): Boolean {
        val low = bits / 8
        for (i in 0 until 32 - low) if (bytes32[i].toInt() != 0) return true
        return false
    }

    /** True if the sign bit (bit N-1) of an N-bit value held in [bytes32] is set. */
    private fun signBitSet(bytes32: ByteArray, bits: Int): Boolean {
        val topByteIndex = 32 - bits / 8
        return (bytes32[topByteIndex].toInt() and 0x80) != 0
    }

    /** True iff [magnitude] == 2^(N-1) (the legitimate most-negative `intN`). */
    private fun isExactlyMinInt(magnitude: ByteArray, bits: Int): Boolean {
        val topByteIndex = 32 - bits / 8
        if ((magnitude[topByteIndex].toInt() and 0xFF) != 0x80) return false
        for (i in topByteIndex + 1 until 32) if (magnitude[i].toInt() != 0) return false
        return true
    }

    /** Big-endian 32-byte encoding of a base-10 string (schoolbook mul-add); throws on overflow. */
    private fun decimalTo32(dec: String): ByteArray {
        val out = ByteArray(32)
        for (ch in dec) {
            require(ch in '0'..'9') { "invalid decimal digit" }
            var carry = ch - '0'
            for (i in 31 downTo 0) {
                val v = (out[i].toInt() and 0xFF) * 10 + carry
                out[i] = (v and 0xFF).toByte()
                carry = v shr 8
            }
            if (carry != 0) throw Eip712Exception("uint256 overflow")
        }
        return out
    }

    private fun leftPad32(bytes: ByteArray): ByteArray =
        ByteArray(32).also { bytes.copyInto(it, destinationOffset = 32 - bytes.size) }

    private fun rightPad32(bytes: ByteArray): ByteArray {
        require(bytes.size <= 32) { "bytesN > 32" }
        return ByteArray(32).also { bytes.copyInto(it) }
    }
}
