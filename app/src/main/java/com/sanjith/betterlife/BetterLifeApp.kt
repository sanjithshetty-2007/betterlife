package com.sanjith.betterlife

import android.app.Application

/** Application entry point. Creates the single [AppContainer] used for manual dependency injection. */
public class BetterLifeApp : Application() {

    public lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
