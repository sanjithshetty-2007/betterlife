package com.sanjith.betterlife

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sanjith.betterlife.data.setup.DeviceCapabilityChecker
import com.sanjith.betterlife.data.setup.ManualCheckStore
import com.sanjith.betterlife.data.setup.SetupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// One DataStore file per process; the delegate guarantees a single instance.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Holds the shared objects (repositories, clock, scheduler) for manual dependency injection.
 * Later prompts add their dependencies here instead of using Hilt or Koin.
 */
public class AppContainer(appContext: Context) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    public val setupRepository: SetupRepository = SetupRepository(
        checker = DeviceCapabilityChecker(appContext),
        manualChecks = ManualCheckStore(appContext.settingsDataStore),
        ioDispatcher = ioDispatcher,
    )
}
