package com.firestarterpro.templartv.data

import android.content.Context
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag

/**
 * How the data layer asks "is this feature on?". The app uses [fromContext]; tests pass their own.
 * Turning a flag off stops new writes of that kind. It never deletes anything already stored.
 */
fun interface FlagGate {
    fun isOn(flag: Flag): Boolean

    companion object {
        val AllOn = FlagGate { true }

        fun fromContext(context: Context): FlagGate {
            val app = context.applicationContext
            return FlagGate { FeatureFlags.isEnabled(app, it) }
        }
    }
}
