package com.shopfinance.app.ui.auth

import com.shopfinance.app.data.auth.AuthError
import com.shopfinance.app.data.auth.AuthOutcome
import com.shopfinance.app.ui.fakes.FakeAuthRepository
import com.shopfinance.app.ui.fakes.FakeAuthRepository.SignInCall
import com.shopfinance.app.ui.fakes.FakeAuthRepository.SignUpCall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var auth: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    private val state: AuthUiState get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        auth = FakeAuthRepository()
        viewModel = AuthViewModel(auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillSignIn(email: String = EMAIL, password: String = PASSWORD) {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    private fun fillSignUp(name: String = NAME, email: String = EMAIL, password: String = PASSWORD) {
        if (state.mode == AuthMode.SIGN_IN) viewModel.toggleMode()
        viewModel.onNameChange(name)
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun startsInSignInModeWithAnEmptyForm() {
        assertEquals(AuthUiState(), state)
        assertEquals(AuthMode.SIGN_IN, state.mode)
    }

    @Test
    fun submitWithAnEmptyFormShowsFieldErrorsAndSendsNothing() = runTest {
        viewModel.submit()
        runCurrent()

        assertEquals(AuthFieldErrors(email = EmailError.EMPTY, password = PasswordError.EMPTY), state.fieldErrors)
        assertFalse(state.isSubmitting)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun signInSendsTheTrimmedEmailAndThePasswordAsTyped() = runTest {
        fillSignIn(email = "  $EMAIL ", password = " pass word ")
        viewModel.submit()
        runCurrent()

        assertEquals(listOf(SignInCall(EMAIL, " pass word ")), auth.signInCalls)
        assertTrue(auth.signUpCalls.isEmpty())
    }

    @Test
    fun signInDoesNotApplyTheNewAccountPasswordMinimum() = runTest {
        fillSignIn(password = "abc")
        viewModel.submit()
        runCurrent()

        assertEquals(listOf(SignInCall(EMAIL, "abc")), auth.signInCalls)
    }

    @Test
    fun submittingShowsProgressUntilTheAnswerArrives() = runTest {
        val answer = CompletableDeferred<AuthOutcome>()
        auth.outcome = answer
        fillSignIn()

        viewModel.submit()
        assertTrue(state.isSubmitting)
        runCurrent()
        assertTrue(state.isSubmitting)

        answer.complete(AuthOutcome.Success)
        runCurrent()
        assertFalse(state.isSubmitting)
        assertNull(state.error)
    }

    @Test
    fun submitAndResetAreIgnoredWhileARequestIsInFlight() = runTest {
        val answer = CompletableDeferred<AuthOutcome>()
        auth.outcome = answer
        fillSignIn()

        viewModel.submit()
        runCurrent()
        viewModel.submit()
        viewModel.sendPasswordReset()
        runCurrent()

        assertEquals(1, auth.signInCalls.size)
        assertTrue(auth.resetCalls.isEmpty())

        answer.complete(AuthOutcome.Success)
        runCurrent()
        assertEquals(1, auth.signInCalls.size)
    }

    @Test
    fun failureShowsTheErrorAndKeepsTheForm() = runTest {
        auth.outcome = CompletableDeferred(AuthOutcome.Failure(AuthError.INVALID_CREDENTIALS))
        fillSignIn()

        viewModel.submit()
        runCurrent()

        assertEquals(AuthError.INVALID_CREDENTIALS, state.error)
        assertFalse(state.isSubmitting)
        assertEquals(EMAIL, state.email)
        assertEquals(PASSWORD, state.password)
    }

    @Test
    fun failedSubmitCanBeTriedAgain() = runTest {
        auth.outcome = CompletableDeferred(AuthOutcome.Failure(AuthError.NETWORK))
        fillSignIn()
        viewModel.submit()
        runCurrent()

        auth.outcome = CompletableDeferred(AuthOutcome.Success)
        viewModel.submit()
        runCurrent()

        assertEquals(2, auth.signInCalls.size)
        assertNull(state.error)
    }

    @Test
    fun aCancelledSubmitUnlocksTheForm() = runTest {
        auth.outcome = CompletableDeferred<AuthOutcome>().also { it.cancel() }
        fillSignIn()
        viewModel.submit()
        runCurrent()

        assertFalse(state.isSubmitting)
        assertNull(state.error)

        auth.outcome = CompletableDeferred(AuthOutcome.Success)
        viewModel.submit()
        runCurrent()
        assertEquals(2, auth.signInCalls.size)
    }

    @Test
    fun dismissErrorClearsTheError() = runTest {
        auth.outcome = CompletableDeferred(AuthOutcome.Failure(AuthError.NETWORK))
        fillSignIn()
        viewModel.submit()
        runCurrent()

        viewModel.dismissError()

        assertNull(state.error)
        assertEquals(EMAIL, state.email)
    }

    @Test
    fun editingAFieldClearsItsOwnErrorAndTheServerError() = runTest {
        viewModel.submit()
        runCurrent()
        assertEquals(EmailError.EMPTY, state.fieldErrors.email)
        assertEquals(PasswordError.EMPTY, state.fieldErrors.password)

        viewModel.onEmailChange("o")

        assertNull(state.fieldErrors.email)
        assertEquals(PasswordError.EMPTY, state.fieldErrors.password)

        auth.outcome = CompletableDeferred(AuthOutcome.Failure(AuthError.INVALID_CREDENTIALS))
        fillSignIn()
        viewModel.submit()
        runCurrent()
        viewModel.onPasswordChange("other password")

        assertNull(state.error)
    }

    @Test
    fun signUpChecksTheNameAndThePasswordLength() = runTest {
        fillSignUp(name = "   ", password = "short")
        viewModel.submit()
        runCurrent()

        assertEquals(
            AuthFieldErrors(name = DisplayNameError.EMPTY, password = PasswordError.TOO_SHORT),
            state.fieldErrors,
        )
        assertTrue(auth.signUpCalls.isEmpty())
    }

    @Test
    fun signUpSendsTheTrimmedNameAndEmailAndThePasswordAsTyped() = runTest {
        fillSignUp(name = "  أبو علي ", email = " $EMAIL", password = " long enough ")
        viewModel.submit()
        runCurrent()

        assertEquals(listOf(SignUpCall("أبو علي", EMAIL, " long enough ")), auth.signUpCalls)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun successResetsTheFormToSignInAndKeepsOnlyTheEmail() = runTest {
        fillSignUp()
        viewModel.submit()
        runCurrent()

        assertEquals(AuthUiState(email = EMAIL), state)
    }

    @Test
    fun signUpFailureStaysInSignUpMode() = runTest {
        auth.outcome = CompletableDeferred(AuthOutcome.Failure(AuthError.EMAIL_IN_USE))
        fillSignUp()
        viewModel.submit()
        runCurrent()

        assertEquals(AuthMode.SIGN_UP, state.mode)
        assertEquals(AuthError.EMAIL_IN_USE, state.error)
        assertEquals(NAME, state.name)
    }

    @Test
    fun toggleModeClearsErrorsAndKeepsTypedValues() = runTest {
        fillSignIn(email = "not an email")
        viewModel.submit()
        runCurrent()
        assertEquals(EmailError.INVALID, state.fieldErrors.email)

        viewModel.toggleMode()

        assertEquals(AuthMode.SIGN_UP, state.mode)
        assertEquals(AuthFieldErrors(), state.fieldErrors)
        assertEquals("not an email", state.email)
        assertEquals(PASSWORD, state.password)

        viewModel.toggleMode()
        assertEquals(AuthMode.SIGN_IN, state.mode)
    }

    @Test
    fun toggleModeIsIgnoredWhileSubmitting() = runTest {
        auth.outcome = CompletableDeferred()
        fillSignIn()
        viewModel.submit()
        runCurrent()

        viewModel.toggleMode()

        assertEquals(AuthMode.SIGN_IN, state.mode)
    }

    @Test
    fun passwordResetNeedsAValidEmail() = runTest {
        viewModel.onEmailChange("owner@")
        viewModel.sendPasswordReset()
        runCurrent()

        assertEquals(AuthFieldErrors(email = EmailError.INVALID), state.fieldErrors)
        assertTrue(auth.resetCalls.isEmpty())
        assertFalse(state.resetEmailSent)
    }

    @Test
    fun passwordResetSendsTheTrimmedEmailAndConfirms() = runTest {
        viewModel.onEmailChange(" $EMAIL ")
        viewModel.sendPasswordReset()
        runCurrent()

        assertEquals(listOf(EMAIL), auth.resetCalls)
        assertTrue(state.resetEmailSent)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
    }

    @Test
    fun passwordResetFailureShowsTheError() = runTest {
        auth.outcome = CompletableDeferred(AuthOutcome.Failure(AuthError.TOO_MANY_REQUESTS))
        viewModel.onEmailChange(EMAIL)
        viewModel.sendPasswordReset()
        runCurrent()

        assertEquals(AuthError.TOO_MANY_REQUESTS, state.error)
        assertFalse(state.resetEmailSent)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun aCancelledPasswordResetUnlocksTheForm() = runTest {
        auth.outcome = CompletableDeferred<AuthOutcome>().also { it.cancel() }
        viewModel.onEmailChange(EMAIL)
        viewModel.sendPasswordReset()
        runCurrent()

        assertFalse(state.isSubmitting)
        assertFalse(state.resetEmailSent)

        viewModel.toggleMode()
        assertEquals(AuthMode.SIGN_UP, state.mode)
    }

    @Test
    fun changingTheEmailHidesTheResetConfirmation() = runTest {
        viewModel.onEmailChange(EMAIL)
        viewModel.sendPasswordReset()
        runCurrent()
        assertTrue(state.resetEmailSent)

        viewModel.onEmailChange("other@example.com")

        assertFalse(state.resetEmailSent)
    }

    private companion object {
        const val NAME = "أبو علي"
        const val EMAIL = "owner@example.com"
        const val PASSWORD = "correct horse"
    }
}
