package com.tneff.cyppie.feature.portfolio

import com.tneff.cyppie.portfolio.Money

/**
 * Locale-aware number/currency format profile (KAN-116). Captures the three things that vary across the
 * 14 shipped locales — the **grouping** separator, the **decimal** separator, and the **currency-symbol
 * placement** — so the formatters stay **float-free** (FR-6) while rendering correctly per locale. The
 * 14 locales collapse to three classes (see [forLanguageTag]); each is unit-tested.
 */
internal data class NumberFormatProfile(
    val groupSeparator: String,
    val decimalSeparator: String,
    val symbolBeforeAmount: Boolean,
    /** Separator between the symbol and the amount (e.g. a no-break space for the suffix style). */
    val symbolSpacing: String,
) {
    companion object {
        private const val NBSP = " "

        /** en · ar · zh — group ",", decimal ".", symbol before (e.g. `$1,234.56`). */
        val US = NumberFormatProfile(groupSeparator = ",", decimalSeparator = ".", symbolBeforeAmount = true, symbolSpacing = "")

        /**
         * da · de · es · pt · vi — group ".", decimal ",", symbol **after** with a no-break space
         * (e.g. `1.000.000,00 €`). tr is strictly symbol-prefix but is approximated here (MVP, 1/6).
         */
        val EU_DOT = NumberFormatProfile(groupSeparator = ".", decimalSeparator = ",", symbolBeforeAmount = false, symbolSpacing = NBSP)

        /** fr · no · pl · ru · sv — group no-break space, decimal ",", symbol after (e.g. `1 000 000,00 €`). */
        val EU_SPACE = NumberFormatProfile(groupSeparator = NBSP, decimalSeparator = ",", symbolBeforeAmount = false, symbolSpacing = NBSP)

        /**
         * Resolves a BCP-47 language tag (e.g. `de`, `fr-FR`, `zh-Hans-CN`) to its format class. Unknown
         * tags fall back to [US]. Digit-shaping (e.g. Arabic-Indic) is out of scope — `ar` uses Latin
         * digits with the [US] grouping for now.
         */
        fun forLanguageTag(tag: String): NumberFormatProfile = when (tag.substringBefore('-').lowercase()) {
            "da", "de", "es", "pt", "tr", "vi" -> EU_DOT
            "fr", "no", "nb", "nn", "pl", "ru", "sv" -> EU_SPACE
            else -> US // en, ar, zh, + any unknown
        }
    }
}

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
