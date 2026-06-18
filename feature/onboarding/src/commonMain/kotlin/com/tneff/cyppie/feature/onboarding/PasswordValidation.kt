package com.tneff.cyppie.feature.onboarding

import com.tneff.cyppie.designsystem.components.PasswordStrength

/** Minimum app-password length (SPEC_ONBOARDING_SCREEN3 §Validierung). */
const val MIN_PASSWORD_LENGTH: Int = 8

/** App-password validation errors (ONB-3), in precedence order. */
enum class PwError { Empty, Whitespace, TooShort, Weak }

/** Number of distinct character classes (upper / lower / digit / symbol) present in [pw]. */
private fun charClasses(pw: String): Int {
    var n = 0
    if (pw.any { it.isUpperCase() }) n++
    if (pw.any { it.isLowerCase() }) n++
    if (pw.any { it.isDigit() }) n++
    if (pw.any { !it.isLetterOrDigit() && !it.isWhitespace() }) n++
    return n
}

/**
 * Rule-based password strength (SPEC_ONBOARDING_SCREEN3 §Stärke-Mapping):
 * - **Strong**: ≥ 10 chars and all four character classes.
 * - **Medium**: ≥ [MIN_PASSWORD_LENGTH] chars and ≥ 2 classes.
 * - **Weak**: anything shorter / a single class.
 * - **None**: empty (indicator hidden).
 */
fun evaluatePasswordStrength(pw: String): PasswordStrength {
    if (pw.isEmpty()) return PasswordStrength.None
    val classes = charClasses(pw)
    return when {
        pw.length >= 10 && classes >= 4 -> PasswordStrength.Strong
        pw.length >= MIN_PASSWORD_LENGTH && classes >= 2 -> PasswordStrength.Medium
        else -> PasswordStrength.Weak
    }
}

/**
 * Validates the app password; `null` means valid (→ "continue" enabled). Precedence: empty →
 * whitespace-only → too short → too weak. "Continue" is enabled only at Medium/Strong strength.
 */
fun validatePassword(pw: String): PwError? = when {
    pw.isEmpty() -> PwError.Empty
    pw.isBlank() -> PwError.Whitespace
    pw.length < MIN_PASSWORD_LENGTH -> PwError.TooShort
    evaluatePasswordStrength(pw) == PasswordStrength.Weak -> PwError.Weak
    else -> null
}
