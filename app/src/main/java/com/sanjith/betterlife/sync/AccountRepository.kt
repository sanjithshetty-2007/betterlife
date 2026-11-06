package com.sanjith.betterlife.sync

import android.util.Log
import com.sanjith.betterlife.domain.sync.AuthError
import com.sanjith.betterlife.domain.sync.AuthRules
import com.sanjith.betterlife.domain.sync.AuthState
import com.sanjith.betterlife.domain.sync.SessionPhase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/**
 * Supabase sign-up, sign-in and sign-out (Prompt S1). [client] is null when the build has no Supabase
 * settings: then everything reports NotConfigured and nothing touches the network. Supabase keeps the
 * session on the phone and refreshes it, so a sign-in survives restarts. Never logs emails, passwords
 * or tokens (agent.md section 3).
 */
public class AccountRepository(private val client: SupabaseClient?, scope: CoroutineScope) {

    private val working = MutableStateFlow(false)
    private val outcome = MutableStateFlow<AuthState?>(null)

    public val state: StateFlow<AuthState> = if (client == null) {
        MutableStateFlow(AuthState.NotConfigured)
    } else {
        combine(client.auth.sessionStatus.map(::phaseAndEmail), working, outcome) { (phase, email), busy, result ->
            AuthRules.stateFor(phase, email, busy, result)
        }.stateIn(scope, SharingStarted.Eagerly, AuthState.Checking)
    }

    public suspend fun signIn(email: String, password: String) {
        run { client ->
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            null
        }
    }

    /** With email confirmation on (Supabase's default), the owner must open the email before signing in. */
    public suspend fun signUp(email: String, password: String) {
        run { client ->
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            if (client.auth.currentSessionOrNull() == null) AuthState.ConfirmEmail(email.trim()) else null
        }
    }

    /** Signing out never locks or wipes local data (docs/PLAN_CHANGES.md, Prompt 28). */
    public suspend fun signOut() {
        run { client ->
            client.auth.signOut()
            null
        }
    }

    /** Clears a shown error or "check your email" message. */
    public fun dismissMessage() {
        outcome.value = null
    }

    // Runs one auth call; [action] returns a state to show afterwards, or null to follow the session.
    private suspend fun run(action: suspend (SupabaseClient) -> AuthState?) {
        val supabase = client ?: return
        working.value = true
        outcome.value = try {
            action(supabase)
        } catch (e: RestException) {
            Log.w(TAG, "Auth request refused: ${e.error}")
            AuthState.Failed(AuthRules.errorFor(e.error), signedInAs())
        } catch (e: HttpRequestException) {
            Log.w(TAG, "No connection to Supabase", e)
            AuthState.Failed(AuthError.NO_INTERNET, signedInAs())
        } catch (e: HttpRequestTimeoutException) {
            Log.w(TAG, "Supabase timed out", e)
            AuthState.Failed(AuthError.NO_INTERNET, signedInAs())
        } catch (e: IOException) {
            Log.w(TAG, "Network error", e)
            AuthState.Failed(AuthError.NO_INTERNET, signedInAs())
        }
        working.value = false
    }

    private fun signedInAs(): String? = client?.auth?.currentUserOrNull()?.email

    private fun phaseAndEmail(status: SessionStatus): Pair<SessionPhase, String?> = when (status) {
        is SessionStatus.Authenticated -> SessionPhase.AUTHENTICATED to status.session.user?.email
        is SessionStatus.NotAuthenticated -> SessionPhase.NOT_AUTHENTICATED to null
        SessionStatus.Initializing -> SessionPhase.INITIALIZING to null
        is SessionStatus.RefreshFailure -> SessionPhase.REFRESH_FAILED to client?.auth?.currentUserOrNull()?.email
    }

    private companion object {
        const val TAG = "Account"
    }
}

/** Builds the client from BuildConfig values, or null when they are empty (sync then stays off). */
public fun createSupabaseOrNull(url: String, key: String): SupabaseClient? {
    if (url.isBlank() || key.isBlank()) return null
    return io.github.jan.supabase.createSupabaseClient(url, key) {
        install(io.github.jan.supabase.auth.Auth)
        install(io.github.jan.supabase.postgrest.Postgrest)
    }
}
