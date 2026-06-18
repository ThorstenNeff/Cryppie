package com.tneff.cyppie.feature.onboarding

import com.tneff.cyppie.designsystem.components.PasswordStrength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * KAN-22 AK1/AK3 — ONB-3 password rules as a pure, multiplatform unit test (`commonTest`).
 *
 * Covers the precedence empty → whitespace → too-short → weak → valid, the strength mapping
 * (None/Weak/Medium/Strong), and the "continue enabled only at Medium/Strong" gate. No real
 * passwords — only synthetic patterns; no plaintext beyond these literals (§4.3 / §5.3).
 */
class PasswordValidationTest {

    @Test
    fun minLengthIsEight() = assertEquals(8, MIN_PASSWORD_LENGTH)

    @Test
    fun validateCoversAllErrorCasesInPrecedence() {
        assertEquals(PwError.Empty, validatePassword(""))
        assertEquals(PwError.Whitespace, validatePassword("     "))      // blank, not empty
        assertEquals(PwError.TooShort, validatePassword("Abc1!"))        // 5 chars
        assertEquals(PwError.TooShort, validatePassword("Abcdef1"))      // 7 chars (boundary −1)
        assertEquals(PwError.Weak, validatePassword("abcdefgh"))         // 8 chars, single class
        assertEquals(PwError.Weak, validatePassword("abcdefghij"))       // 10 chars, single class
    }

    @Test
    fun validateReturnsNullForAcceptablePasswords() {
        assertNull(validatePassword("Abcdefgh"))      // 8 chars, upper+lower = 2 classes → Medium
        assertNull(validatePassword("Abcdefg1!"))     // 9 chars, 4 classes → Medium
        assertNull(validatePassword("Abcdefghij1!"))  // 12 chars, 4 classes → Strong
    }

    @Test
    fun strengthMappingMatchesSpec() {
        assertEquals(PasswordStrength.None, evaluatePasswordStrength(""))
        assertEquals(PasswordStrength.Weak, evaluatePasswordStrength("abcdefgh"))      // 8, 1 class
        assertEquals(PasswordStrength.Weak, evaluatePasswordStrength("abcdefghij"))    // 10, 1 class
        assertEquals(PasswordStrength.Medium, evaluatePasswordStrength("Abcdefgh"))    // 8, 2 classes
        assertEquals(PasswordStrength.Medium, evaluatePasswordStrength("Abcdefg1!"))   // 9, 4 classes (<10)
        assertEquals(PasswordStrength.Strong, evaluatePasswordStrength("Abcdefghij1!")) // 10+, 4 classes
    }

    @Test
    fun continueGateMatchesStrength_validIffMediumOrStrong() {
        // valid (validatePassword==null) exactly when strength is Medium or Strong.
        val samples = listOf("", "   ", "abc", "abcdefgh", "Abcdefgh", "Abcdefg1!", "Abcdefghij1!")
        for (pw in samples) {
            val valid = validatePassword(pw) == null
            val strong = evaluatePasswordStrength(pw).let { it == PasswordStrength.Medium || it == PasswordStrength.Strong }
            assertEquals(strong, valid, "continue-gate mismatch for \"$pw\"")
        }
    }

    @Test
    fun whitespaceOnlyIsRejectedEvenAtLength() {
        assertEquals(PwError.Whitespace, validatePassword("          ")) // 10 spaces
    }
}
