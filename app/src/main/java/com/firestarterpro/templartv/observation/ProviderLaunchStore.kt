package com.firestarterpro.templartv.observation

import android.content.Context
import android.content.SharedPreferences
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag

/**
 * Remembers when a provider app was last opened from Templar. Used two ways: as weaker supporting
 * evidence for a title that starts playing soon after ("opened from Templar"), and so the home
 * screen can order provider shelves by most recently used. Only provider apps are stored, only a
 * timestamp per package, and only on this device. Needs [Flag.PROVIDER_RECENT_SHELVES].
 */
class ProviderLaunchStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences
        get() = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Notes that [packageName] was just opened from Templar. Ignored if it is not a known provider app. */
    fun mark(packageName: String, at: Long = clock()) {
        if (ProviderCatalog.providerFor(packageName) == null) return
        if (!FeatureFlags.isEnabled(app, Flag.PROVIDER_RECENT_SHELVES)) return
        runCatching { prefs.edit().putLong(KEY + packageName, at).apply() }
    }

    /** True if [packageName] was opened from Templar no more than [windowMs] before [at]. */
    fun launchedWithin(packageName: String, at: Long, windowMs: Long): Boolean {
        val t = runCatching { prefs.getLong(KEY + packageName, 0L) }.getOrDefault(0L)
        return t > 0 && t <= at && at - t <= windowMs
    }

    /** The most recent time any app of [provider] was opened from Templar, or 0 if never. */
    fun lastLaunch(provider: String): Long =
        ProviderCatalog.packagesFor(provider).maxOfOrNull { runCatching { prefs.getLong(KEY + it, 0L) }.getOrDefault(0L) } ?: 0L

    private companion object {
        const val FILE = "templar_provider_launches"
        const val KEY = "last:"
    }
}
