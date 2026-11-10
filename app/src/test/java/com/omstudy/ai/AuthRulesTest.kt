package com.omstudy.ai

import com.omstudy.ai.data.AuthRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {
    @Test fun validEmail() = assertTrue(AuthRules.isValidEmail("om@example.com"))
    @Test fun emailWithSpacesAroundIsTrimmed() = assertTrue(AuthRules.isValidEmail("  om@example.com "))
    @Test fun emailWithoutAt() = assertFalse(AuthRules.isValidEmail("omexample.com"))
    @Test fun emailWithoutDomainDot() = assertFalse(AuthRules.isValidEmail("om@example"))
    @Test fun emptyEmail() = assertFalse(AuthRules.isValidEmail(""))

    @Test fun signUpOk() =
        assertNull(AuthRules.validateSignUp("ओम", "om@example.com", "password1", "password1"))

    @Test fun signUpBlankName() =
        assertNotNull(AuthRules.validateSignUp("  ", "om@example.com", "password1", "password1"))

    @Test fun signUpNameTooLong() =
        assertNotNull(AuthRules.validateSignUp("a".repeat(41), "om@example.com", "password1", "password1"))

    @Test fun signUpShortPassword() =
        assertNotNull(AuthRules.validateSignUp("ओम", "om@example.com", "pass", "pass"))

    @Test fun signUpMismatchedPasswords() =
        assertEquals("दोनों पासवर्ड एक जैसे नहीं हैं।", AuthRules.validateSignUp("ओम", "om@example.com", "password1", "password2"))

    @Test fun loginOk() = assertNull(AuthRules.validateLogin("om@example.com", "x"))
    @Test fun loginEmptyPassword() = assertNotNull(AuthRules.validateLogin("om@example.com", ""))
    @Test fun loginBadEmail() = assertNotNull(AuthRules.validateLogin("nope", "x"))
}
