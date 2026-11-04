package com.sanjith.betterlife.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.domain.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
public class SettingsRepositoryTest {

    private lateinit var file: File
    private lateinit var scope: CoroutineScope

    @Before
    public fun setUp() {
        file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "settings-test.preferences_pb")
        file.delete()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    public fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    public fun freshInstallReadsDefaultsAndChangesRoundTrip() = runTest {
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(AppSettings(), repo.current())

        repo.update { it.copy(quietHoursEnabled = true, quietHoursStart = LocalTime.of(23, 30), weekStart = DayOfWeek.SUNDAY) }

        val saved = repo.current()
        assertEquals(true, saved.quietHoursEnabled)
        assertEquals(LocalTime.of(23, 30), saved.quietHoursStart)
        assertEquals(DayOfWeek.SUNDAY, saved.weekStart)
        assertEquals(AppSettings().snoozeTonight, saved.snoozeTonight)
    }

    @Test
    public fun invalidStoredValueFallsBackToDefaultsInsteadOfCrashing() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        store.edit { it[intPreferencesKey("rollover_hour")] = 99 }

        assertEquals(AppSettings(), SettingsRepository(store).current())
    }
}
