package com.tneff.cyppie.feature.strat

/**
 * Test-tag contract for the Smart-Strategies flow (KAN-166) — **testTag == the strat_ key** of the element
 * (the surface QA's Maestro flows target; treat as an API). Structurally parallel to [CopyTestTags].
 */
object StrategyTestTags {
    // Setup (Strat1-Setup)
    const val SETUP_SCREEN = "strat_setup_title"
    const val ADD_TOKEN = "strat_add_token"
    const val TOTAL = "strat_total"
    const val BUDGET_INPUT = "strat_budget"
    const val CONTINUE = "strat_continue"

    // Review / Confirm (Strat-Confirm) — 2-section sell-side disclosure
    const val REVIEW_SCREEN = "strat_review_title"
    const val DISCLOSURE = "strat_you_authorize"
    const val CAP = "strat_cap"
    const val PASSWORD = "strat_password"
    const val AUTHORIZE = "strat_authorize"
    const val ERROR = "strat_err_verify"
    const val DONE_SCREEN = "strat_done_screen"

    // Active overview (Strat2-List) + on-chain Revoke
    const val ACTIVE_SCREEN = "strat_active"
    const val NEW_CTA = "strat_new"
    const val EMPTY = "strat_empty"
    const val LOAD_ERROR = "strat_load_error"
    const val SESSION_STATUS = "strat_status_active"
    const val REVOKE = "strat_revoke"
    const val REVOKE_TITLE = "strat_revoke_title"
    const val REVOKE_CONFIRM = "strat_revoke_confirm"
    const val REVOKED = "strat_revoked"

    // Detail (Strat3-Detail) — current vs target + drift + pause/resume
    const val DETAIL_CURRENT = "strat_current"
    const val DETAIL_DRIFT = "strat_drift"
    const val PAUSE = "strat_pause"
    const val RESUME = "strat_resume"
}
