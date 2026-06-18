package com.tneff.cyppie.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * KAN-24 AK1/AK2 — ONB-4 confirm-password match rules as a pure, multiplatform unit test
 * (`commonTest`). `validateConfirmPassword(confirm, original)`: empty → [ConfirmError.Empty];
 * differing → [ConfirmError.Mismatch]; equal → `null` (valid → continue enabled). No real
 * passwords — only synthetic literals (§5.3).
 */
class ConfirmPasswordValidationTest {

    private val original = "Abcdefg1!"

    @Test
    fun emptyConfirmIsEmptyError() {
        assertEquals(ConfirmError.Empty, validateConfirmPassword("", original))
    }

    @Test
    fun differingConfirmIsMismatch() {
        assertEquals(ConfirmError.Mismatch, validateConfirmPassword("different", original))
        assertEquals(ConfirmError.Mismatch, validateConfirmPassword("Abcdefg1", original))   // missing last char
        assertEquals(ConfirmError.Mismatch, validateConfirmPassword("abcdefg1!", original))  // case differs
        assertEquals(ConfirmError.Mismatch, validateConfirmPassword("Abcdefg1! ", original)) // trailing space
    }

    @Test
    fun exactMatchIsValid() {
        assertNull(validateConfirmPassword(original, original))
        assertNull(validateConfirmPassword("x", "x"))
    }

    @Test
    fun emptyTakesPrecedenceOverMismatch() {
        // An empty confirm is reported as Empty even when the original is non-empty.
        assertEquals(ConfirmError.Empty, validateConfirmPassword("", original))
    }
}
