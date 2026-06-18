package com.tneff.cyppie.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Utf8Test {

    @Test
    fun encodesAsciiBmpAndSupplementaryLikeStdlib() {
        for (s in listOf("", "password", "münze", "пароль", "密码", "🔐 secret 😀")) {
            assertContentEquals(s.encodeToByteArray(), Utf8.encode(s.toCharArray()), "mismatch for: $s")
        }
    }

    @Test
    fun loneSurrogatesBecomeReplacementChar() {
        val replacement = byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()) // U+FFFD
        // lone high surrogate, lone low surrogate, and a high surrogate at end of input
        assertContentEquals(replacement, Utf8.encode(charArrayOf('\uD800')))
        assertContentEquals(replacement, Utf8.encode(charArrayOf('\uDC00')))
        assertContentEquals("a".encodeToByteArray() + replacement, Utf8.encode(charArrayOf('a', '\uD83D')))
        // round-trips to the Unicode replacement character (no raw WTF-8)
        assertEquals("�", Utf8.encode(charArrayOf('\uD800')).decodeToString())
    }
}
