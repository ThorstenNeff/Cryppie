package com.tneff.cyppie.feature.copy

/**
 * Test-tag contract for the Copy / Follow-Trader flow (KAN-155) — per the spec, **testTag == the copy_ key**
 * of the element. The surface QA's Maestro flows target; treat as an API (rename only with the QA flows).
 */
object CopyTestTags {
    // Screen 1 — choose trader
    const val SELECT_SCREEN = "copy_select_title"
    const val TRADER_INPUT = "copy_trader_label"
    const val CONTINUE = "copy_continue" // the primary "Continue" action (one visible per step at a time)

    // Mode select (KAN-161) — fixed vs dynamic mirror mode
    const val MODE_SCREEN = "copy_mode_title"
    const val MODE_FIXED = "copy_mode_fixed"
    const val MODE_DYNAMIC = "copy_mode_dynamic"
    const val TOKEN_PICK = "copy_token_pick"     // fixed mode: the receive-token INPUT field (ModeSelect)
    const val RECEIVES = "copy_receives"         // disclosure advisory: the mirror/receive token value
    const val DYN_RISK = "copy_dyn_risk_title"   // dynamic mode: the danger consent banner
    const val DYN_RISK_ACK = "copy_dyn_risk_ack" // dynamic mode: the mandatory acknowledgement checkbox

    // Budget / cap
    const val BUDGET_SCREEN = "copy_budget_title"
    const val BUDGET_INPUT = "copy_budget_label"
    const val BUDGET_CAVEAT = "copy_budget_caveat"

    // Screen 3 — verified disclosure + authorize/sign
    const val REVIEW_SCREEN = "copy_review_title"
    const val DISCLOSURE = "copy_you_authorize"
    const val DISCLOSURE_CAP = "copy_cap"
    const val PASSWORD = "copy_password"
    const val AUTHORIZE = "copy_authorize"
    const val ERROR = "copy_err_verify" // generic disclosure-screen error surface

    // Done
    const val DONE_SCREEN = "copy_done_screen"
}
