package com.tneff.cyppie.storage

/**
 * UTF-8 encoding for a [CharArray] **without** going through an immutable [String] (M2): a `String`
 * can't be zeroized and would leave the password lingering on the heap. The transient buffer here is
 * zeroized; the returned bytes are the caller's to wipe.
 */
internal object Utf8 {
    fun encode(chars: CharArray): ByteArray {
        val tmp = ByteArray(chars.size * 4) // max 4 bytes per code point
        var n = 0
        var i = 0
        while (i < chars.size) {
            val c = chars[i].code
            when {
                c < 0x80 -> tmp[n++] = c.toByte()
                c < 0x800 -> {
                    tmp[n++] = (0xC0 or (c shr 6)).toByte()
                    tmp[n++] = (0x80 or (c and 0x3F)).toByte()
                }
                c in 0xD800..0xDBFF && i + 1 < chars.size && chars[i + 1].code in 0xDC00..0xDFFF -> {
                    val cp = 0x10000 + ((c - 0xD800) shl 10) + (chars[i + 1].code - 0xDC00)
                    i++
                    tmp[n++] = (0xF0 or (cp shr 18)).toByte()
                    tmp[n++] = (0x80 or ((cp shr 12) and 0x3F)).toByte()
                    tmp[n++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                    tmp[n++] = (0x80 or (cp and 0x3F)).toByte()
                }
                c in 0xD800..0xDFFF -> {
                    // Unpaired surrogate (lone high without a low, or a stray low) → U+FFFD,
                    // not raw WTF-8 (N1). Matches String.encodeToByteArray's replacement behaviour.
                    tmp[n++] = 0xEF.toByte()
                    tmp[n++] = 0xBF.toByte()
                    tmp[n++] = 0xBD.toByte()
                }
                else -> {
                    tmp[n++] = (0xE0 or (c shr 12)).toByte()
                    tmp[n++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                    tmp[n++] = (0x80 or (c and 0x3F)).toByte()
                }
            }
            i++
        }
        val out = tmp.copyOf(n)
        tmp.fill(0)
        return out
    }
}
