package com.sanjith.betterlife

import android.app.Application
import com.sanjith.betterlife.reminders.NotificationChannels

/** Application entry point. Creates the single [AppContainer] used for manual dependency injection. */
public class BetterLifeApp : Application() {

    public lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Idempotent: creating an existing channel only updates its name.
        NotificationChannels.createAll(this)
    }
}
