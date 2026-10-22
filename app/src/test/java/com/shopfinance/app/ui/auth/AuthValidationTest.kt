package com.shopfinance.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun commonEmailsAreAccepted() {
        listOf(
            "owner@example.com",
            "abu.ali+shop@mail.example.lb",
            "a@b.co",
            "  owner@example.com  ",
        ).forEach { email ->
            assertNull(email, AuthValidation.validateEmail(email))
        }
    }

    @Test
    fun emptyOrBlankEmailIsEmpty() {
        assertEquals(EmailError.EMPTY, AuthValidation.validateEmail(""))
        assertEquals(EmailError.EMPTY, AuthValidation.validateEmail("   "))
    }

    @Test
    fun malformedEmailsAreInvalid() {
        listOf(
            "owner",
            "owner@",
            "@example.com",
            "owner@example",
            "owner@example.",
            "owner@.com",
            "owner@example..com",
            "owner@@example.com",
            "own er@example.com",
            "owner@exa mple.com",
        ).forEach { email ->
            assertEquals(email, EmailError.INVALID, AuthValidation.validateEmail(email))
        }
    }

    @Test
    fun signInOnlyNeedsAPassword() {
        assertEquals(PasswordError.EMPTY, AuthValidation.validateSignInPassword(""))
        assertNull(AuthValidation.validateSignInPassword("abc"))
    }

    @Test
    fun newPasswordNeedsTheMinimumLength() {
        assertEquals(8, AuthValidation.MIN_PASSWORD_LENGTH)
        assertEquals(PasswordError.EMPTY, AuthValidation.validateNewPassword(""))
        assertEquals(PasswordError.TOO_SHORT, AuthValidation.validateNewPassword("1234567"))
        assertNull(AuthValidation.validateNewPassword("12345678"))
    }

    @Test
    fun passwordsAreNotTrimmed() {
        // Eight characters including the spaces: accepted as typed.
        assertNull(AuthValidation.validateNewPassword("  123456"))
        assertNull(AuthValidation.validateSignInPassword(" "))
    }

    @Test
    fun displayNameIsCheckedAfterTrimming() {
        assertEquals(DisplayNameError.EMPTY, AuthValidation.validateDisplayName(""))
        assertEquals(DisplayNameError.EMPTY, AuthValidation.validateDisplayName(" \t "))
        assertNull(AuthValidation.validateDisplayName("  أبو علي  "))
        assertNull(AuthValidation.validateDisplayName("س"))
    }

    @Test
    fun displayNameHasAMaximumLength() {
        assertEquals(50, AuthValidation.MAX_NAME_LENGTH)
        assertNull(AuthValidation.validateDisplayName("ن".repeat(AuthValidation.MAX_NAME_LENGTH)))
        assertNull(AuthValidation.validateDisplayName("  " + "ن".repeat(AuthValidation.MAX_NAME_LENGTH) + "  "))
        assertEquals(
            DisplayNameError.TOO_LONG,
            AuthValidation.validateDisplayName("ن".repeat(AuthValidation.MAX_NAME_LENGTH + 1)),
        )
    }

    @Test
    fun signInFormChecksEmailAndPasswordOnly() {
        assertEquals(
            AuthFieldErrors(email = EmailError.INVALID, password = PasswordError.EMPTY),
            AuthValidation.validateSignIn("owner", ""),
        )
        assertFalse(AuthValidation.validateSignIn("owner@example.com", "abc").hasAny)
    }

    @Test
    fun signUpFormChecksAllThreeFields() {
        val errors = AuthValidation.validateSignUp(name = "", email = "", password = "short")

        assertEquals(
            AuthFieldErrors(
                name = DisplayNameError.EMPTY,
                email = EmailError.EMPTY,
                password = PasswordError.TOO_SHORT,
            ),
            errors,
        )
        assertTrue(errors.hasAny)
        assertFalse(AuthValidation.validateSignUp("أبو علي", "owner@example.com", "long enough").hasAny)
    }
}
