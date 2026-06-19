package com.tneff.cyppie.feature.portfolio

/**
 * Stable testTags for the portfolio feature screens (mirrors `WalletTestTags`). Tags share the
 * `pf_*` vocabulary with the i18n keys so design, copy and tests stay aligned (PRD-03 / SPEC §5.6).
 */
object PortfolioTestTags {
    const val SCREEN = "pf_screen"
    const val TOTAL_VALUE = "pf_total_value"
    const val CHANGE_24H = "pf_change_24h"
    const val PNL = "pf_pnl"
    const val APPROXIMATE_BANNER = "pf_approximate_banner"
    const val ALLOCATION_LIST = "pf_allocation_list"
    const val LOADING = "pf_loading"
    const val EMPTY = "pf_empty"
    const val ERROR = "pf_error"
    const val RETRY = "pf_retry"

    /** Allocation row `i` (descending by value). */
    fun allocationItem(i: Int): String = "pf_allocation_item_$i"
}
