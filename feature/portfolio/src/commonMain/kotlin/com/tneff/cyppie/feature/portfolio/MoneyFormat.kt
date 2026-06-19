package com.tneff.cyppie.feature.portfolio

import com.tneff.cyppie.market.Money
import com.tneff.cyppie.designsystem.NumberFormatProfile


/**
 * Presentation-only fixed-point formatting for [Money]. Stays **float-free** (FR-6): the value is
 * `minorUnits / 10^scale`, rendered by string slicing — never `Double` — so what the user sees is
 * exactly the integer the valuation layer computed. Grouping / decimal separators + symbol placement
 * follow [profile] (KAN-116, locale-aware); the currency renders as a symbol for the common fiats, else
 * its ISO code. [profile] defaults to [NumberFormatProfile.US].
 */
internal fun Money.formatted(profile: NumberFormatProfile = NumberFormatProfile.US): String {
    val negative = minorUnits < 0L
    // toString() avoids abs() overflow at Long.MIN_VALUE (a saturated cap is a legal input).
    val rawDigits = if (negative) minorUnits.toString().substring(1) else minorUnits.toString()
    val padded = rawDigits.padStart(scale + 1, '0')
    val intDigits = padded.substring(0, padded.length - scale)
    val fracDigits = if (scale > 0) padded.substring(padded.length - scale) else ""

    val grouped = intDigits.reversed().chunked(3).joinToString(profile.groupSeparator).reversed()
    val fraction = if (scale > 0) "${profile.decimalSeparator}$fracDigits" else ""
    val number = "$grouped$fraction"
    val symbol = currencySymbol(currency)
    val withSymbol = if (profile.symbolBeforeAmount) {
        "$symbol${profile.symbolSpacing}$number"
    } else {
        "$number${profile.symbolSpacing}$symbol"
    }
    return if (negative) "-$withSymbol" else withSymbol
}

/** Like [formatted] but always shows the sign — `+` for gains, `-` for losses (FR-2 24h / P&L). */
internal fun Money.formattedSigned(profile: NumberFormatProfile = NumberFormatProfile.US): String =
    if (minorUnits > 0L) "+${formatted(profile)}" else formatted(profile)

/** Basis points → a locale-aware percent string (`12.34%` / `12,34%`), float-free (bps/100, two decimals). */
internal fun formatBps(bps: Int, profile: NumberFormatProfile = NumberFormatProfile.US): String {
    // Sign from bps, not from `whole` — for bps in (-99..-1) `whole` is 0 and would drop the minus (KAN-107 L1).
    val negative = bps < 0
    val abs = if (negative) -bps else bps
    val sign = if (negative) "-" else ""
    return "$sign${abs / 100}${profile.decimalSeparator}${(abs % 100).toString().padStart(2, '0')}%"
}

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> "${currency.uppercase()} "
}
