package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.evm.Quantity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * EIP-712 (`eth_signTypedData_v4`) digest builder. Produces the 32-byte hash to sign:
 * `keccak256(0x1901 ‖ domainSeparator ‖ hashStruct(primaryType, message))`, where
 * `hashStruct(s) = keccak256(typeHash(s) ‖ encodeData(s))` and `typeHash = keccak256(encodeType)`.
 *
 * Security-sensitive + encoding-heavy (ADR-0014 ethos) — validated against the canonical EIP-712
 * "Mail" example; authoritative WC vectors are KAN-63. Addresses are decoded as raw 20 bytes (no
 * EIP-55 enforcement — dapp typed-data is often un-checksummed).
 */
internal object Eip712 {

    private val json = Json { ignoreUnknownKeys = true }

    /** The 32-byte EIP-712 digest for a `eth_signTypedData_v4` payload [typedDataJson]. */
    fun encode(typedDataJson: String): ByteArray {
        val root = json.parseToJsonElement(typedDataJson).jsonObject
        val types = root.getValue("types").jsonObject
        val primaryType = root.getValue("primaryType").jsonPrimitive.content
        val domain = root.getValue("domain").jsonObject
        val message = root.getValue("message").jsonObject
        val domainSeparator = hashStruct("EIP712Domain", domain, types)
        val messageHash = hashStruct(primaryType, message, types)
        return Keccak.keccak256(byteArrayOf(0x19, 0x01) + domainSeparator + messageHash)
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
            val elementType = type.substring(0, type.lastIndexOf('['))
            var enc = ByteArray(0)
            for (e in value.jsonArray) enc += encodeValue(elementType, e, types)
            return Keccak.keccak256(enc)
        }
        if (types[type] != null) return hashStruct(type, value.jsonObject, types) // nested struct
        return when {
            type == "string" -> Keccak.keccak256(value.jsonPrimitive.content.encodeToByteArray())
            type == "bytes" -> Keccak.keccak256(Hex.decodeOrNull(value.jsonPrimitive.content) ?: ByteArray(0))
            type == "bool" -> ByteArray(32).also { it[31] = if (value.jsonPrimitive.boolean) 1 else 0 }
            type == "address" -> leftPad32(decodeAddress(value.jsonPrimitive.content))
            type.startsWith("uint") -> uintTo32(value.jsonPrimitive.content)
            type.startsWith("int") -> intTo32(value.jsonPrimitive.content)
            type.startsWith("bytes") -> rightPad32(Hex.decodeOrNull(value.jsonPrimitive.content) ?: ByteArray(0))
            else -> throw WalletConnectException.UnsupportedRequest("Unsupported EIP-712 type: $type")
        }
    }

    private fun decodeAddress(hex: String): ByteArray {
        val bytes = Hex.decodeOrNull(hex) ?: throw WalletConnectException.UnsupportedRequest("Bad address: $hex")
        if (bytes.size != 20) throw WalletConnectException.UnsupportedRequest("Address must be 20 bytes")
        return bytes
    }

    private fun uintTo32(value: String): ByteArray =
        if (value.startsWith("0x") || value.startsWith("0X")) Quantity.ofHex(value).toBytes32() else decimalTo32(value)

    private fun intTo32(value: String): ByteArray {
        if (!value.startsWith("-")) return uintTo32(value)
        val magnitude = decimalTo32(value.substring(1)) // two's complement of |value|
        for (i in magnitude.indices) magnitude[i] = magnitude[i].toInt().inv().toByte()
        var carry = 1
        for (i in 31 downTo 0) {
            val v = (magnitude[i].toInt() and 0xFF) + carry
            magnitude[i] = (v and 0xFF).toByte()
            carry = v shr 8
        }
        return magnitude
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
            if (carry != 0) throw WalletConnectException.UnsupportedRequest("uint256 overflow")
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
