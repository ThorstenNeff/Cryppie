package com.tneff.cyppie.feature.copy

/**
 * Test-tag contract for the Copy / Follow-Trader flow (KAN-155) — per the spec, **testTag == the copy_ key**
 * of the element. The surface QA's Maestro flows target; treat as an API (rename only with the QA flows).
 */
object CopyTestTags {
    // Screen 1 — choose trader
    const val SELECT_SCREEN = "copy_select_title"
    const val TRADER_INPUT = "copy_trader_label"
    const val CONTINUE = "copy_continue" // the primary "Continue" action (screens 1 & 2; one visible at a time)

    // Screen 2 — budget / cap
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
