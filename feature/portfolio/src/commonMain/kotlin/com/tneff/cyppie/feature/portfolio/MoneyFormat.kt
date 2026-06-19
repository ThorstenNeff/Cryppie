package com.tneff.cyppie.feature.portfolio

import com.tneff.cyppie.portfolio.Money

/**
 * Presentation-only fixed-point formatting for [Money]. Stays **float-free** (FR-6): the value is
 * `minorUnits / 10^scale`, rendered by string slicing — never `Double` — so what the user sees is
 * exactly the integer the valuation layer computed. Thousands are grouped; the currency renders as a
 * symbol for the common fiats, else its ISO code. Locale-aware grouping/symbol placement is a later
 * refinement (KAN-109); this is the deterministic baseline the tests pin.
 */
internal fun Money.formatted(): String {
    val negative = minorUnits < 0L
    // toString() avoids abs() overflow at Long.MIN_VALUE (a saturated cap is a legal input).
    val rawDigits = if (negative) minorUnits.toString().substring(1) else minorUnits.toString()
    val padded = rawDigits.padStart(scale + 1, '0')
    val intDigits = padded.substring(0, padded.length - scale)
    val fracDigits = if (scale > 0) padded.substring(padded.length - scale) else ""

    val grouped = intDigits.reversed().chunked(3).joinToString(",").reversed()
    val symbol = currencySymbol(currency)
    val sign = if (negative) "-" else ""
    val fraction = if (scale > 0) ".$fracDigits" else ""
    return "$sign$symbol$grouped$fraction"
}

/** Like [formatted] but always shows the sign — `+` for gains, `-` for losses (FR-2 24h / P&L). */
internal fun Money.formattedSigned(): String = if (minorUnits > 0L) "+${formatted()}" else formatted()

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> "${currency.uppercase()} "
}
