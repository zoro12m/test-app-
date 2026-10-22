package com.shopfinance.app.ui.auth

/** Why the email field was rejected by [AuthValidation]. */
enum class EmailError { EMPTY, INVALID }

/** Why the password field was rejected by [AuthValidation]. */
enum class PasswordError {
    EMPTY,

    /** Shorter than [AuthValidation.MIN_PASSWORD_LENGTH]; only checked when creating an account. */
    TOO_SHORT,
}

/** Why the display name field was rejected by [AuthValidation]. */
enum class DisplayNameError {
    /** Empty, or nothing but whitespace. */
    EMPTY,

    /** Longer than [AuthValidation.MAX_NAME_LENGTH] characters after trimming. */
    TOO_LONG,
}

/** The problems found in the sign-in or sign-up form, one per field; null means the field is fine. */
data class AuthFieldErrors(
    val name: DisplayNameError? = null,
    val email: EmailError? = null,
    val password: PasswordError? = null,
) {
    val hasAny: Boolean get() = name != null || email != null || password != null
}

/**
 * Form checks that run before anything is sent to Firebase, so the person gets a clear
 * message at once instead of a server error. Pure, so it is unit-tested on a plain JVM.
 */
object AuthValidation {
    /** Firebase itself accepts 6; 8 is the app's own minimum for new accounts. */
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NAME_LENGTH = 50

    /** local@domain.tld: no spaces, one @, and a domain made of dot-separated, non-empty labels. */
    private val EMAIL_PATTERN = Regex("""[^\s@]+@[^\s@.]+(\.[^\s@.]+)+""")

    /** Checks the email after trimming, as it will be sent. */
    fun validateEmail(raw: String): EmailError? {
        val email = raw.trim()
        return when {
            email.isEmpty() -> EmailError.EMPTY
            !EMAIL_PATTERN.matches(email) -> EmailError.INVALID
            else -> null
        }
    }

    /** Signing in only needs a password: accounts may predate the current minimum length. */
    fun validateSignInPassword(password: String): PasswordError? =
        if (password.isEmpty()) PasswordError.EMPTY else null

    /** A new account's password. Passwords are never trimmed. */
    fun validateNewPassword(password: String): PasswordError? = when {
        password.isEmpty() -> PasswordError.EMPTY
        password.length < MIN_PASSWORD_LENGTH -> PasswordError.TOO_SHORT
        else -> null
    }

    /** Checks the display name after trimming, as it will be saved. */
    fun validateDisplayName(raw: String): DisplayNameError? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> DisplayNameError.EMPTY
            name.length > MAX_NAME_LENGTH -> DisplayNameError.TOO_LONG
            else -> null
        }
    }

    fun validateSignIn(email: String, password: String): AuthFieldErrors = AuthFieldErrors(
        email = validateEmail(email),
        password = validateSignInPassword(password),
    )

    fun validateSignUp(name: String, email: String, password: String): AuthFieldErrors = AuthFieldErrors(
        name = validateDisplayName(name),
        email = validateEmail(email),
        password = validateNewPassword(password),
    )
}
