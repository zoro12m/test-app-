package com.shopfinance.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class AuthErrorMapperTest {

    @Test
    fun invalidEmailCode() {
        assertEquals(AuthError.INVALID_EMAIL, mapAuthErrorCode("ERROR_INVALID_EMAIL"))
    }

    @Test
    fun wrongPasswordUnknownUserAndInvalidCredentialAllMapToInvalidCredentials() {
        // Firebase does not reveal which one it was, and neither does the app.
        assertEquals(AuthError.INVALID_CREDENTIALS, mapAuthErrorCode("ERROR_WRONG_PASSWORD"))
        assertEquals(AuthError.INVALID_CREDENTIALS, mapAuthErrorCode("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthError.INVALID_CREDENTIALS, mapAuthErrorCode("ERROR_INVALID_CREDENTIAL"))
    }

    @Test
    fun emailInUseCode() {
        assertEquals(AuthError.EMAIL_IN_USE, mapAuthErrorCode("ERROR_EMAIL_ALREADY_IN_USE"))
    }

    @Test
    fun weakPasswordCode() {
        assertEquals(AuthError.WEAK_PASSWORD, mapAuthErrorCode("ERROR_WEAK_PASSWORD"))
    }

    @Test
    fun userDisabledCode() {
        assertEquals(AuthError.USER_DISABLED, mapAuthErrorCode("ERROR_USER_DISABLED"))
    }

    @Test
    fun tooManyRequestsCode() {
        assertEquals(AuthError.TOO_MANY_REQUESTS, mapAuthErrorCode("ERROR_TOO_MANY_REQUESTS"))
    }

    @Test
    fun nullEmptyAndUnknownCodesMapToUnknown() {
        assertEquals(AuthError.UNKNOWN, mapAuthErrorCode(null))
        assertEquals(AuthError.UNKNOWN, mapAuthErrorCode(""))
        assertEquals(AuthError.UNKNOWN, mapAuthErrorCode("ERROR_SOMETHING_NEW"))
    }

    @Test
    fun codesAreMatchedExactly() {
        assertEquals(AuthError.UNKNOWN, mapAuthErrorCode("error_invalid_email"))
        assertEquals(AuthError.UNKNOWN, mapAuthErrorCode(" ERROR_INVALID_EMAIL"))
    }

    @Test
    fun nonFirebaseExceptionsMapToUnknown() {
        assertEquals(AuthError.UNKNOWN, mapAuthException(RuntimeException("boom")))
        assertEquals(AuthError.UNKNOWN, mapAuthException(IOException("offline")))
        assertEquals(AuthError.UNKNOWN, mapAuthException(IllegalArgumentException("Given String is empty or null")))
    }

    @Test
    fun cancellationIsRethrownNotMapped() {
        val cancellation = CancellationException("scope cancelled")
        val thrown = assertThrows(CancellationException::class.java) { mapAuthException(cancellation) }
        assertSame(cancellation, thrown)
    }
}
