package com.shopfinance.app.data.auth

import kotlinx.coroutines.flow.Flow

/** The signed-in person, as the app needs it. Never holds a password or token. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

/** Why an auth call failed, already mapped from Firebase so the UI never sees Firebase types. */
enum class AuthError {
    /** Wrong email or password. Firebase does not say which, to avoid revealing who has an account. */
    INVALID_CREDENTIALS,
    INVALID_EMAIL,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    USER_DISABLED,
    TOO_MANY_REQUESTS,
    NETWORK,
    UNKNOWN,
}

sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Failure(val error: AuthError) : AuthOutcome
}

interface AuthRepository {
    /** The current user, re-emitted on every sign-in and sign-out. Emits the current value first. */
    val currentUser: Flow<AuthUser?>

    suspend fun signIn(email: String, password: String): AuthOutcome

    /** Creates the account, signs it in and saves [displayName] on the Firebase profile. */
    suspend fun signUp(displayName: String, email: String, password: String): AuthOutcome

    suspend fun sendPasswordReset(email: String): AuthOutcome

    fun signOut()
}
