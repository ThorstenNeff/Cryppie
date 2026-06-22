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
    const val TOKEN_PICK = "copy_token_pick"     // fixed mode: the receive-token picker entry + sheet title (ModeSelect)
    const val TOKEN_SEARCH = "copy_token_search" // the token-picker search field (KAN-168 F5)
    const val TOKEN_NONE = "copy_token_none"     // the picker's empty state (KAN-168 F5)
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
    const val ADVISORY = "copy_advisory" // the ℹ️ advisory section banner (KAN-168 F4)
    const val DISCLOSURE_CAP = "copy_cap"
    const val PASSWORD = "copy_password"
    const val AUTHORIZE = "copy_authorize"
    const val ERROR = "copy_err_verify" // generic disclosure-screen error surface

    // Done
    const val DONE_SCREEN = "copy_done_screen"

    // Active overview (Copy0-Active, KAN-157) + on-chain Revoke
    const val ACTIVE_SCREEN = "copy_active"       // the Active-copies landing
    const val COPY_CTA = "copy_title"             // "Copy a trader" CTA → Follow-flow
    const val EMPTY = "copy_empty"                // no-sessions state
    const val LOAD_ERROR = "copy_load_error"      // list load-error banner
    const val SESSION_STATUS = "copy_status"      // per-row status badge (icon+text)
    const val REVOKE = "copy_revoke"              // per-row revoke action
    const val REVOKE_TITLE = "copy_revoke_title"  // the Revoke-Confirm dialog
    const val REVOKE_CONFIRM = "copy_revoke_confirm" // the danger confirm button
    const val REVOKED = "copy_revoked"            // success confirmation
}
