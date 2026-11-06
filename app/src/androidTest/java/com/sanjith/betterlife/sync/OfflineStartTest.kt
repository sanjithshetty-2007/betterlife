package com.sanjith.betterlife.sync

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.domain.sync.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt S1: the app starts and works with no network and no account. The reminder path never
 * touches sync (AuthRulesTest checks no reminder code imports Supabase or Ktor).
 */
@RunWith(AndroidJUnit4::class)
public class OfflineStartTest {

    @Test
    public fun theAppStartsAndReachesHomeWithoutSigningIn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    public fun withoutSupabaseSettingsTheAccountIsSimplyOffAndActionsDoNothing() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val account = AccountRepository(client = null, scope = scope)
            assertEquals(AuthState.NotConfigured, account.state.value)
            account.signIn("me@example.com", "secret123")
            account.signOut()
            assertEquals(AuthState.NotConfigured, account.state.value)
        } finally {
            scope.cancel()
        }
    }
}
