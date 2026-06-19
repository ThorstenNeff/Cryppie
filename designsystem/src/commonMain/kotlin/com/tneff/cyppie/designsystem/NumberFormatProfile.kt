package com.tneff.cyppie.designsystem

/**
 * Locale-aware number/currency format profile (KAN-116, shared in `:designsystem` since KAN-120 so the
 * portfolio **and** wallet amount formatters use one source). Captures the three things that vary across
 * the 14 shipped locales — the **grouping** separator, the **decimal** separator, and **currency-symbol
 * placement** — so formatters stay **float-free** (FR-6) while rendering correctly per locale. The 14
 * locales collapse to three classes (see [forLanguageTag]); each is unit-tested.
 */
data class NumberFormatProfile(
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
