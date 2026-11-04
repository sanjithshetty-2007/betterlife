package com.sanjith.betterlife.core

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Log of spike decisions and outcomes, shown on the debug screens and mirrored to logcat.
 * Kept in a file so events from a receiver in a fresh process (after swipe-away or reboot) survive.
 * Never pass private content (note text, titles of real items) to it.
 *
 * [ioDispatcher] must run one task at a time (limitedParallelism(1)) so lines stay in order.
 */
public class DebugLog(
    private val file: File,
    private val nowMillis: () -> Long,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    public val entries: StateFlow<List<String>> = _entries
    private var lastWrite: Job = scope.launch(ioDispatcher) { loadFromFile() }

    public fun add(message: String) {
        // Info, not debug: OriginOS drops Log.d lines from logcat.
        Log.i(TAG, message)
        val line = "${TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis()))}  $message"
        _entries.update { (it + line).takeLast(MAX_ENTRIES) }
        lastWrite = scope.launch(ioDispatcher) { append(line) }
    }

    /** Receivers call this before finishing so their lines reach the file before the process may die. */
    public suspend fun awaitWrites() {
        lastWrite.join()
    }

    public fun clear() {
        _entries.value = emptyList()
        lastWrite = scope.launch(ioDispatcher) { file.delete() }
    }

    private fun loadFromFile() {
        val saved = try {
            if (file.exists()) file.readLines().takeLast(MAX_ENTRIES) else emptyList()
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the debug log", e)
            emptyList()
        }
        // Lines added before loading finished come after the saved ones.
        _entries.update { (saved + it).takeLast(MAX_ENTRIES) }
    }

    private fun append(line: String) {
        try {
            file.appendText(line + "\n")
            if (file.length() > MAX_FILE_BYTES) file.writeText(_entries.value.joinToString("\n", postfix = "\n"))
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the debug log", e)
        }
    }

    private companion object {
        const val TAG = "BetterLifeDebug"
        const val MAX_ENTRIES = 300
        const val MAX_FILE_BYTES = 128 * 1024L
        val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}
