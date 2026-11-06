package com.sanjith.betterlife.domain.sync

/** Why a sign-in, sign-up or sign-out did not work, in terms the Account screen can explain. */
public enum class AuthError {
    WRONG_PASSWORD,
    EMAIL_NOT_CONFIRMED,
    ACCOUNT_EXISTS,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    TOO_MANY_TRIES,
    NO_INTERNET,
    UNKNOWN,
}

/** What the Account screen shows (Prompt S1). The app works fully in every one of these states. */
public sealed interface AuthState {
    /** No Supabase settings in this build (local.properties was empty): sync is simply off. */
    public data object NotConfigured : AuthState

    /** Restoring a saved session at start-up. */
    public data object Checking : AuthState

    public data object SignedOut : AuthState

    /** A request is on its way. */
    public data object Working : AuthState

    /** Signed up; Supabase sent a confirmation email that must be opened before signing in. */
    public data class ConfirmEmail(val email: String) : AuthState

    public data class SignedIn(val email: String) : AuthState

    /** The last action failed; the owner is still signed out (or still signed in, for sign-out). */
    public data class Failed(val error: AuthError, val signedInAs: String? = null) : AuthState
}

/** The parts of Supabase's session status the app cares about (mapped in sync/, kept pure here). */
public enum class SessionPhase { INITIALIZING, AUTHENTICATED, NOT_AUTHENTICATED, REFRESH_FAILED }

public object AuthRules {

    /**
     * Supabase Auth error codes (https://supabase.com/docs/guides/auth/debugging/error-codes) to the
     * reasons the screen explains. Unknown or missing codes are UNKNOWN.
     */
    public fun errorFor(code: String?): AuthError = when (code) {
        "invalid_credentials", "invalid_grant" -> AuthError.WRONG_PASSWORD
        "email_not_confirmed" -> AuthError.EMAIL_NOT_CONFIRMED
        "user_already_exists", "email_exists" -> AuthError.ACCOUNT_EXISTS
        "weak_password" -> AuthError.WEAK_PASSWORD
        "email_address_invalid", "validation_failed" -> AuthError.INVALID_EMAIL
        "over_request_rate_limit", "over_email_send_rate_limit", "over_sms_send_rate_limit" -> AuthError.TOO_MANY_TRIES
        else -> AuthError.UNKNOWN
    }

    /**
     * The screen state from Supabase's session phase plus what the screen itself is doing. An action
     * in progress or its result wins over the stored session; an expired session whose refresh failed
     * (no internet) still counts as signed in, because the tokens are kept and refreshed later.
     *
     * Example: AUTHENTICATED with email "me@x.in", nothing pending -> SignedIn("me@x.in").
     */
    public fun stateFor(phase: SessionPhase, email: String?, working: Boolean, outcome: AuthState?): AuthState = when {
        working -> AuthState.Working
        outcome != null -> outcome
        phase == SessionPhase.INITIALIZING -> AuthState.Checking
        (phase == SessionPhase.AUTHENTICATED || phase == SessionPhase.REFRESH_FAILED) && email != null -> AuthState.SignedIn(email)
        else -> AuthState.SignedOut
    }

    /** Supabase's default minimum password length. */
    public const val MIN_PASSWORD_LENGTH: Int = 6

    /** Enough to catch typos before asking the server; the server has the final word. */
    public fun looksLikeEmail(text: String): Boolean {
        val t = text.trim()
        val at = t.indexOf('@')
        return at > 0 && at == t.lastIndexOf('@') && t.indexOf('.', at) > at + 1 && !t.endsWith(".") && t.none { it.isWhitespace() }
    }
}
