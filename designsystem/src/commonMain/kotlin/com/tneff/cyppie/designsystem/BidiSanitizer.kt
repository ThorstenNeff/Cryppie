package com.tneff.cyppie.designsystem

/**
 * Neutralizes Unicode characters that can spoof what a security disclosure *displays* vs. what it
 * *means* — the "Trojan Source" class (CVE-2021-42574). Attacker-controlled strings shown in a
 * disclosure (an ERC-20 token name/symbol, a recipient, a `personal_sign`/typed-data message, a memo)
 * could embed a right-to-left override or zero-width char to reorder/hide text — e.g. make a hostile
 * address render like a trusted one. `isISOControl()` does **not** catch these (KAN-122 / KAN-62-M2).
 *
 * Each dangerous codepoint is replaced with its visible `\uXXXX` escape: that both **neutralizes** it
 * (it's now ordinary ASCII, no formatting effect) and **surfaces** it (the user sees the tampering).
 * Ordinary text is returned unchanged. Pure/allocation-free on the common (clean) path.
 *
 * Apply at the display boundary of any untrusted disclosure string. Reused by the WC disclosure +
 * Send-Confirm (KAN-122).
 */
object BidiSanitizer {

    // Codepoints (not literal chars) so this source can't itself be tampered with by the very chars it
    // guards against. Two families: bidi formatting/overrides (reordering) + zero-width/invisible (hiding).
    private val DANGEROUS: Set<Int> = setOf(
        0x200E, 0x200F,                         // LRM, RLM
        0x061C,                                 // ALM (Arabic letter mark)
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E, // LRE, RLE, PDF, LRO, RLO
        0x2066, 0x2067, 0x2068, 0x2069,         // LRI, RLI, FSI, PDI
        0x200B, 0x200C, 0x200D,                 // ZWSP, ZWNJ, ZWJ
        0x2060,                                 // WORD JOINER
        0xFEFF,                                 // ZERO WIDTH NO-BREAK SPACE / BOM
    )

    /** Returns [text] with every bidi-control / zero-width char rendered as a visible `\uXXXX` escape. */
    fun sanitize(text: String): String {
        if (text.none { it.code in DANGEROUS }) return text
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            if (c.code in DANGEROUS) {
                sb.append("\\u").append(c.code.toString(16).padStart(4, '0').uppercase())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
