package com.firestarterpro.templartv.observation

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short in-memory log of what the observer saw and decided, for Settings → Diagnostics and for
 * checking the observer on the TV. It never holds a title, an episode name or any metadata value:
 * only package names, field names and decision codes. Lost when the app restarts. Local only.
 */
object ObserverDiagnostics {
    private const val TAG = "TemplarObserver"
    private const val MAX = 60
    private val lines = ArrayDeque<String>()

    @Volatile
    var enabled: Boolean = true

    @Synchronized
    fun note(packageOrKey: String, code: String, now: Long = System.currentTimeMillis()) {
        if (!enabled) return
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
        val line = "$time $packageOrKey $code"
        lines.addLast(line)
        while (lines.size > MAX) lines.removeFirst()
        Log.d(TAG, "$packageOrKey $code")
    }

    /**
     * Debug only ([com.firestarterpro.templartv.Flag.DEBUG_METADATA_VALUES], off by default). Goes to
     * logcat only, never into the in-memory list above, and contains real titles while it is on.
     */
    fun debugValue(packageName: String, key: String, value: String) {
        Log.d(TAG, "$packageName $key=$value")
    }

    @Synchronized
    fun recent(): List<String> = lines.toList()
}
