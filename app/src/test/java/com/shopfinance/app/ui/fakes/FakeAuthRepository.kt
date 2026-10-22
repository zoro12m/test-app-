package com.shopfinance.app.ui.fakes

import com.shopfinance.app.data.auth.AuthOutcome
import com.shopfinance.app.data.auth.AuthRepository
import com.shopfinance.app.data.auth.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [AuthRepository]. Tests set [user] to sign someone in or out, and script call
 * results with [outcome]: a completed deferred answers at once, an uncompleted one keeps
 * the call in flight until the test completes it.
 */
class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {

    val user = MutableStateFlow(initialUser)
    override val currentUser: StateFlow<AuthUser?> = user

    var outcome: CompletableDeferred<AuthOutcome> = CompletableDeferred(AuthOutcome.Success)

    val signInCalls = mutableListOf<SignInCall>()
    val signUpCalls = mutableListOf<SignUpCall>()
    val resetCalls = mutableListOf<String>()
    var signOutCalls = 0
        private set

    override suspend fun signIn(email: String, password: String): AuthOutcome {
        signInCalls += SignInCall(email, password)
        return outcome.await()
    }

    override suspend fun signUp(displayName: String, email: String, password: String): AuthOutcome {
        signUpCalls += SignUpCall(displayName, email, password)
        return outcome.await()
    }

    override suspend fun sendPasswordReset(email: String): AuthOutcome {
        resetCalls += email
        return outcome.await()
    }

    override fun signOut() {
        signOutCalls++
        user.value = null
    }

    data class SignInCall(val email: String, val password: String)

    data class SignUpCall(val displayName: String, val email: String, val password: String)
}
