package com.tneff.cyppie.feature.wallet

import com.tneff.cyppie.evm.Quantity

/**
 * Formats a raw on-chain integer amount ([Quantity], big-endian wei-style) to a human decimal string
 * with [decimals] fraction places (ERC-20 `decimals` / 18 for native). Big-integer-safe — wei needs
 * more than 64 bits, so this never goes through `Long`. The fraction is **truncated** (not rounded)
 * to [maxFractionDigits] and trailing zeros trimmed, so a balance is never overstated. No fiat
 * valuation (PRD-02); UI-layer conversion per `Quantity`'s contract (L2 does no decimal math).
 */
fun formatTokenAmount(amount: Quantity, decimals: Int, maxFractionDigits: Int = 6): String {
    val digits = bytesToDecimal(amount.toMinimalBytes())
    if (decimals <= 0) return digits
    val padded = digits.padStart(decimals + 1, '0') // guarantee ≥1 integer digit
    val intPart = padded.substring(0, padded.length - decimals)
    var frac = padded.substring(padded.length - decimals)
    if (frac.length > maxFractionDigits) frac = frac.substring(0, maxFractionDigits)
    frac = frac.trimEnd('0')
    return if (frac.isEmpty()) intPart else "$intPart.$frac"
}

/**
 * Parses a user-entered decimal [input] into a raw [Quantity] given the asset's [decimals] (KAN-110
 * Send). Returns null on anything not a clean non-negative decimal (empty, sign, stray chars, or more
 * than one '.'). The fraction is **truncated** to [decimals] places (sub-unit input can't create
 * value out of thin air). Big-integer-safe — builds the integer string and goes through `Quantity`,
 * never `Long` (wei exceeds 64 bits). Inverse of [formatTokenAmount].
 */
fun parseTokenAmount(input: String, decimals: Int): Quantity? {
    val t = input.trim()
    if (t.isEmpty() || t == ".") return null
    val dot = t.indexOf('.')
    if (dot >= 0 && t.indexOf('.', dot + 1) >= 0) return null // more than one '.'
    val intPart = if (dot < 0) t else t.substring(0, dot)
    val fracRaw = if (dot < 0) "" else t.substring(dot + 1)
    if (intPart.any { it !in '0'..'9' } || fracRaw.any { it !in '0'..'9' }) return null
    val frac = if (fracRaw.length >= decimals) fracRaw.substring(0, decimals) else fracRaw.padEnd(decimals, '0')
    val digits = (intPart + frac).trimStart('0').ifEmpty { "0" }
    return Quantity.ofBytes(decimalToBytes(digits))
}

/** Base-10 string → big-endian base-256 magnitude (no BigInteger); empty bytes for zero. Inverse of [bytesToDecimal]. */
private fun decimalToBytes(decimal: String): ByteArray {
    if (decimal.all { it == '0' }) return ByteArray(0)
    val le = ArrayList<Int>().apply { add(0) } // little-endian base-256 digits
    for (ch in decimal) {
        var carry = ch - '0'
        for (i in le.indices) {
            val v = le[i] * 10 + carry
            le[i] = v and 0xFF
            carry = v shr 8
        }
        while (carry > 0) { le.add(carry and 0xFF); carry = carry shr 8 }
    }
    return ByteArray(le.size) { le[le.size - 1 - it].toByte() } // → big-endian
}

/** Big-endian base-256 magnitude → base-10 string (no BigInteger in commonMain); "0" for empty/zero. */
private fun bytesToDecimal(bytes: ByteArray): String {
    if (bytes.isEmpty()) return "0"
    val decimal = ArrayList<Int>().apply { add(0) } // little-endian base-10 digits
    for (b in bytes) {
        var carry = b.toInt() and 0xFF
        for (i in decimal.indices) {
            val v = decimal[i] * 256 + carry
            decimal[i] = v % 10
            carry = v / 10
        }
        while (carry > 0) {
            decimal.add(carry % 10)
            carry /= 10
        }
    }
    val sb = StringBuilder(decimal.size)
    for (i in decimal.indices.reversed()) sb.append(decimal[i])
    return sb.toString().trimStart('0').ifEmpty { "0" }
}
