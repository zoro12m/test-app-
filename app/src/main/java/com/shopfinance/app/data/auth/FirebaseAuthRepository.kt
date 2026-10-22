package com.shopfinance.app.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * [AuthRepository] backed by Firebase Authentication (email and password).
 *
 * Every failure is mapped to an [AuthError] by [mapAuthException], so callers never see
 * Firebase types. Emails are trimmed before they reach Firebase; passwords are passed as typed.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
) : AuthRepository {

    /**
     * Bumped after the profile changes. The auth state listener only fires on sign-in and
     * sign-out, so without this the display name saved by [signUp] would not show up in
     * [currentUser] until the next sign-in.
     */
    private val profileVersion = MutableStateFlow(0)

    override val currentUser: Flow<AuthUser?> =
        combine(authStateChanges(), profileVersion) { _, _ -> auth.currentUser?.toAuthUser() }
            .distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): AuthOutcome {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) return AuthOutcome.Failure(AuthError.INVALID_EMAIL)
        if (password.isEmpty()) return AuthOutcome.Failure(AuthError.INVALID_CREDENTIALS)
        return attempt {
            auth.signInWithEmailAndPassword(trimmedEmail, password).await()
        }
    }

    override suspend fun signUp(displayName: String, email: String, password: String): AuthOutcome {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) return AuthOutcome.Failure(AuthError.INVALID_EMAIL)
        if (password.isEmpty()) return AuthOutcome.Failure(AuthError.WEAK_PASSWORD)
        return attempt {
            val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: auth.currentUser
            if (user != null) saveDisplayName(user, displayName.trim())
        }
    }

    /**
     * An unknown email counts as success. Projects without email-enumeration protection fail
     * with ERROR_USER_NOT_FOUND (mapped to INVALID_CREDENTIALS), which would tell the person
     * whether the email has an account and show a password error where no password was typed.
     * The success message already says "if this email has an account".
     */
    override suspend fun sendPasswordReset(email: String): AuthOutcome {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) return AuthOutcome.Failure(AuthError.INVALID_EMAIL)
        val outcome = attempt {
            auth.sendPasswordResetEmail(trimmedEmail).await()
        }
        return if (outcome == AuthOutcome.Failure(AuthError.INVALID_CREDENTIALS)) AuthOutcome.Success else outcome
    }

    override fun signOut() {
        auth.signOut()
    }

    /**
     * Emits right away (so [currentUser] starts with the current user) and again on every
     * sign-in and sign-out. The listener also fires when it is added; [currentUser] drops
     * that repeat with distinctUntilChanged.
     */
    private fun authStateChanges(): Flow<Unit> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(Unit) }
        trySend(Unit)
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Saves [displayName] on the new account's profile. The account already exists and is
     * signed in at this point, so a failure here is logged, not reported: failing the whole
     * sign-up would make the person retry and hit EMAIL_IN_USE.
     */
    private suspend fun saveDisplayName(user: FirebaseUser, displayName: String) {
        if (displayName.isEmpty()) return
        try {
            val request = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            user.updateProfile(request).await()
            profileVersion.update { it + 1 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Saving the display name failed", e)
        }
    }

    /** Runs [block] and turns its outcome into an [AuthOutcome]. Cancellation is rethrown. */
    private suspend fun attempt(block: suspend () -> Unit): AuthOutcome =
        try {
            block()
            AuthOutcome.Success
        } catch (e: Exception) {
            AuthOutcome.Failure(mapAuthException(e))
        }

    private companion object {
        const val TAG = "FirebaseAuthRepository"
    }
}

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    email = email,
    displayName = displayName?.takeIf { it.isNotBlank() },
)
