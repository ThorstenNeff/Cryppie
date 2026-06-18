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
