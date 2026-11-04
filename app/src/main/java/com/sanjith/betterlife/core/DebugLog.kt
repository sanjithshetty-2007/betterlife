package com.sanjith.betterlife.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * In-memory log of spike decisions and outcomes, shown on the debug screen and mirrored to logcat.
 * Never pass private content (note text, titles of real items) to it.
 */
public class DebugLog(private val nowMillis: () -> Long) {

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    public val entries: StateFlow<List<String>> = _entries

    public fun add(message: String) {
        // Info, not debug: OriginOS drops Log.d lines from logcat.
        Log.i(TAG, message)
        val stamp = TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis()))
        _entries.update { (it + "$stamp  $message").takeLast(MAX_ENTRIES) }
    }

    public fun clear() {
        _entries.value = emptyList()
    }

    private companion object {
        const val TAG = "BetterLifeDebug"
        const val MAX_ENTRIES = 200
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}
