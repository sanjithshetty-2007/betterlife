package com.sanjith.betterlife.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class AuthRulesTest {

    @Test
    public fun supabaseErrorCodesBecomePlainReasons() {
        assertEquals(AuthError.WRONG_PASSWORD, AuthRules.errorFor("invalid_credentials"))
        assertEquals(AuthError.EMAIL_NOT_CONFIRMED, AuthRules.errorFor("email_not_confirmed"))
        assertEquals(AuthError.ACCOUNT_EXISTS, AuthRules.errorFor("user_already_exists"))
        assertEquals(AuthError.ACCOUNT_EXISTS, AuthRules.errorFor("email_exists"))
        assertEquals(AuthError.WEAK_PASSWORD, AuthRules.errorFor("weak_password"))
        assertEquals(AuthError.INVALID_EMAIL, AuthRules.errorFor("email_address_invalid"))
        assertEquals(AuthError.TOO_MANY_TRIES, AuthRules.errorFor("over_email_send_rate_limit"))
        assertEquals(AuthError.UNKNOWN, AuthRules.errorFor("something_new"))
        assertEquals(AuthError.UNKNOWN, AuthRules.errorFor(null))
    }

    @Test
    public fun sessionPhasesMapToScreenStates() {
        assertEquals(AuthState.Checking, AuthRules.stateFor(SessionPhase.INITIALIZING, null, working = false, outcome = null))
        assertEquals(AuthState.SignedOut, AuthRules.stateFor(SessionPhase.NOT_AUTHENTICATED, null, false, null))
        assertEquals(AuthState.SignedIn("me@x.in"), AuthRules.stateFor(SessionPhase.AUTHENTICATED, "me@x.in", false, null))
        // No internet to refresh the token: still signed in, Supabase refreshes it later.
        assertEquals(AuthState.SignedIn("me@x.in"), AuthRules.stateFor(SessionPhase.REFRESH_FAILED, "me@x.in", false, null))
    }

    @Test
    public fun anActionInProgressOrItsResultWinsOverTheSession() {
        assertEquals(AuthState.Working, AuthRules.stateFor(SessionPhase.NOT_AUTHENTICATED, null, working = true, outcome = null))
        val failed = AuthState.Failed(AuthError.NO_INTERNET)
        assertEquals(failed, AuthRules.stateFor(SessionPhase.NOT_AUTHENTICATED, null, false, failed))
        val confirm = AuthState.ConfirmEmail("me@x.in")
        assertEquals(confirm, AuthRules.stateFor(SessionPhase.NOT_AUTHENTICATED, null, false, confirm))
    }

    @Test
    public fun emailCheckCatchesTyposOnly() {
        assertTrue(AuthRules.looksLikeEmail("sanjith@example.com"))
        assertTrue(AuthRules.looksLikeEmail("  a.b+c@mail.co.in "))
        listOf("", "sanjith", "@example.com", "a@b", "a@b.", "a@@b.com", "a b@c.com", "a@.com").forEach {
            assertFalse("'$it' should be rejected", AuthRules.looksLikeEmail(it))
        }
    }

    /**
     * agent.md section 5 / Prompt 30: the network is used only by the sync/ package (and later the link
     * preview job). Reminders, the domain logic and the database must never import Supabase or Ktor.
     */
    @Test
    public fun onlyTheSyncPackageUsesTheNetwork() {
        val root = listOf(File("src/main/java/com/sanjith/betterlife"), File("app/src/main/java/com/sanjith/betterlife")).first { it.isDirectory }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.relativeTo(root).path.replace('\\', '/').startsWith("sync/") || it.name == "AppContainer.kt" }
            .filter { file -> file.readLines().any { it.startsWith("import io.github.jan.supabase") || it.startsWith("import io.ktor") } }
            .map { it.relativeTo(root).path }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
