package com.shopfinance.app.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Maps a `FirebaseAuthException.errorCode` string to an [AuthError]. Pure (no Firebase
 * types), so it is unit-tested on a plain JVM. Null and unknown codes map to
 * [AuthError.UNKNOWN].
 */
fun mapAuthErrorCode(code: String?): AuthError = when (code) {
    "ERROR_INVALID_EMAIL" -> AuthError.INVALID_EMAIL
    "ERROR_WRONG_PASSWORD",
    "ERROR_USER_NOT_FOUND",
    "ERROR_INVALID_CREDENTIAL",
    -> AuthError.INVALID_CREDENTIALS
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EMAIL_IN_USE
    "ERROR_WEAK_PASSWORD" -> AuthError.WEAK_PASSWORD
    "ERROR_USER_DISABLED" -> AuthError.USER_DISABLED
    "ERROR_TOO_MANY_REQUESTS" -> AuthError.TOO_MANY_REQUESTS
    else -> AuthError.UNKNOWN
}

/**
 * Maps the exception a Firebase Auth call failed with to an [AuthError].
 *
 * A [CancellationException] is rethrown, never mapped, so coroutine cancellation keeps
 * working. Anything that is not a known Firebase exception maps to [AuthError.UNKNOWN].
 */
fun mapAuthException(error: Throwable): AuthError = when (error) {
    is CancellationException -> throw error
    is FirebaseNetworkException -> AuthError.NETWORK
    is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
    is FirebaseAuthException -> mapAuthErrorCode(error.errorCode)
    else -> AuthError.UNKNOWN
}
