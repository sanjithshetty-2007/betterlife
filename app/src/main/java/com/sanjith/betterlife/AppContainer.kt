package com.sanjith.betterlife

import android.content.Context

/**
 * Holds the shared objects (repositories, clock, scheduler) for manual dependency injection.
 * Empty for now; later prompts add their dependencies here instead of using Hilt or Koin.
 */
public class AppContainer(
    @Suppress("unused") private val appContext: Context,
)
