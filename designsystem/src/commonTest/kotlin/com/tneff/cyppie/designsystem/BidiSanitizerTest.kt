package com.tneff.cyppie.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BidiSanitizerTest {

    @Test
    fun cleanText_isReturnedUnchanged() {
        assertEquals("USDC", BidiSanitizer.sanitize("USDC"))
        assertEquals("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", BidiSanitizer.sanitize("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))
        // ASCII control chars are not our concern here (the disclosure already hex-encodes those) — only
        // bidi/zero-width. A plain newline passes through.
        assertEquals("a\nb", BidiSanitizer.sanitize("a\nb"))
    }

    @Test
    fun cleanText_returnsSameInstance_noAllocation() {
        val s = "Wrapped Ether"
        assertSame(s, BidiSanitizer.sanitize(s))
    }

    @Test
    fun rtlOverride_isNeutralizedAndVisible() {
        // The classic spoof: RLO (U+202E) reorders the following text. (Built from the codepoint so this
        // test source carries no literal control chars.)
        val spoof = "abc" + 0x202E.toChar() + "cba"
        val out = BidiSanitizer.sanitize(spoof)
        assertEquals("abc\\u202Ecba", out)
        // The control char itself is gone — what's left is inert ASCII.
        assertTrue(out.none { it.code == 0x202E })
    }

    @Test
    fun allBidiAndZeroWidth_areEscaped() {
        val codepoints = listOf(
            0x200E, 0x200F, 0x061C,
            0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
            0x2066, 0x2067, 0x2068, 0x2069,
            0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF,
        )
        for (cp in codepoints) {
            val out = BidiSanitizer.sanitize("x${cp.toChar()}y")
            val hex = cp.toString(16).padStart(4, '0').uppercase()
            assertEquals("x\\u${hex}y", out, "codepoint U+$hex not sanitized")
        }
    }

    @Test
    fun multipleControls_allReplaced() {
        val input = "${0x202E.toChar()}a${0x200B.toChar()}b${0x2069.toChar()}"
        val out = BidiSanitizer.sanitize(input)
        assertEquals("\\u202Ea\\u200Bb\\u2069", out)
    }
}
