package com.tneff.cyppie.feature.onboarding

/**
 * Single source of truth for the onboarding `testTag` IDs — the stable-selector **contract with the
 * test agent** (`../Tests/.maestro/README.md`, §5.1). Maestro selects via `id:`, which the root maps
 * from `testTag` (Android resource-id / iOS accessibility-identifier, see
 * [enableTestTagsAsResourceId]).
 *
 * Scheme: `onb_<screen>_<element>`. Every onboarding screen built in KAN-5+ MUST tag its interactive
 * / asserted elements with the constants below instead of string literals, so the code and the
 * Maestro contract cannot drift. IDs not yet wired to a widget belong to screens not built yet
 * (their ONB-* ticket wires them); the welcome landing already uses [WELCOME_START]/[WELCOME_IMPORT].
 */
object OnboardingTestTags {

    // Screen 1 — Welcome
    const val WELCOME_START = "onb_welcome_start"
    const val WELCOME_IMPORT = "onb_welcome_import"
    const val WELCOME_START_ERROR_DIALOG = "onb_welcome_start_error_dialog"

    // Screen 2 — Choose path
    const val PATH_SCREEN = "onb_path_screen"
    const val PATH_CREATE = "onb_path_create"
    const val PATH_IMPORT = "onb_path_import"
    const val PATH_OFFLINE_BANNER = "onb_path_offline_banner"

    // Screen 3 — Set password
    const val PASSWORD_INPUT = "onb_password_input"
    const val PASSWORD_STRENGTH = "onb_password_strength"
    const val PASSWORD_CONTINUE = "onb_password_continue"
    const val PASSWORD_ERROR = "onb_password_error"

    // Screen 4 — Confirm password
    const val CONFIRM_INPUT = "onb_confirm_input"
    const val CONFIRM_CONTINUE = "onb_confirm_continue"
    const val CONFIRM_ERROR = "onb_confirm_error"

    // Screen 5 — Seed entry (import)
    const val SEED_WORDCOUNT_12 = "onb_seed_wordcount_12"
    const val SEED_WORDCOUNT_24 = "onb_seed_wordcount_24"
    const val SEED_PASTE = "onb_seed_paste"
    const val SEED_IMPORT = "onb_seed_import"
    const val SEED_ERROR_BANNER = "onb_seed_error_banner"

    /** Seed input cell `n` (1-based), `onb_seed_cell_{1..24}`. */
    fun seedCell(n: Int): String = "onb_seed_cell_$n"

    // Screen 6 — Show seed (create)
    const val SHOW_SEED_REVEAL = "onb_show_seed_reveal"
    const val SHOW_SEED_CONTINUE = "onb_show_seed_continue"
    const val SHOW_SEED_ERROR_DIALOG = "onb_show_seed_error_dialog"

    // Screen 7 — Confirm backup
    const val BACKUP_CONTINUE = "onb_backup_continue"
    const val BACKUP_ERROR = "onb_backup_error"

    /** Backup challenge cell `n`, `onb_backup_cell_{n}`. */
    fun backupCell(n: Int): String = "onb_backup_cell_$n"

    // Screen 8 — Wallet setup
    const val SETUP_PROGRESS = "onb_setup_progress"
    const val SETUP_SUCCESS = "onb_setup_success"
    const val SETUP_ERROR_DIALOG = "onb_setup_error_dialog"
    const val SETUP_STORAGE_BANNER = "onb_setup_storage_banner"

    // Screen 9 — Biometrics
    const val BIOMETRIC_ENABLE = "onb_biometric_enable"
    const val BIOMETRIC_SKIP = "onb_biometric_skip"
    const val BIOMETRIC_UNAVAILABLE = "onb_biometric_unavailable"
}
