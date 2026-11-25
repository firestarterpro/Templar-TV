package com.firestarterpro.templartv.quickmenu

import android.content.Context
import android.content.Intent
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.QuickSettingsActivity
import com.firestarterpro.templartv.data.TemplarData
import com.firestarterpro.templartv.service.VistaForeground
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The one public way to open the Templar quick menu.
 *
 * - Inside Templar's home screen ([VistaForeground.isForeground]) it asks the in-app panel to open,
 *   so the home screen stays put underneath and focus comes back to where it was.
 * - Anywhere else it starts [QuickSettingsActivity] (translucent, over the current app) with
 *   [ACTION_OPEN_QUICK_MENU].
 *
 * Callers in this process (the companion API, the accessibility service, shortcuts) should call
 * [open]. The activity is not exported, so other apps can't send the action.
 */
object QuickMenuController {

    const val ACTION_OPEN_QUICK_MENU = "com.firestarterpro.templartv.action.OPEN_QUICK_MENU"

    /** Per-profile setting key in the B2 `profile_settings` table. */
    const val LAYOUT_SETTING_KEY = "quick_menu_layout"

    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Ticks when something asked the in-app menu to open. Collected by the home screen root. */
    val openRequests: SharedFlow<Unit> = requests.asSharedFlow()

    fun isEnabled(context: Context): Boolean = FeatureFlags.isEnabled(context, Flag.QUICK_MENU)

    /**
     * Opens the quick menu. Returns false when [Flag.QUICK_MENU] is off (nothing happens; callers
     * that want the old quick-settings panel start [QuickSettingsActivity] themselves) or when the
     * activity could not be started.
     */
    fun open(context: Context): Boolean {
        if (!isEnabled(context)) return false
        if (VistaForeground.isForeground && requests.tryEmit(Unit)) return true
        return runCatching {
            context.startActivity(
                Intent(context, QuickSettingsActivity::class.java)
                    .setAction(ACTION_OPEN_QUICK_MENU)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    // Layout persistence: per profile, in the B2 data layer. Hide is a flag in the stored value.

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLayout(context: Context): Flow<QuickMenuLayout> {
        val profiles = TemplarData.get(context).profiles
        return profiles.activeProfileId.flatMapLatest { id ->
            profiles.observeSetting(id, LAYOUT_SETTING_KEY).map { QuickMenuLayout.decode(it) }
        }
    }

    suspend fun saveLayout(context: Context, layout: QuickMenuLayout) {
        val profiles = TemplarData.get(context).profiles
        profiles.ensureDefault() // the setting row needs its profile (foreign key)
        profiles.putSetting(profiles.activeProfileId.value, LAYOUT_SETTING_KEY, layout.encode())
    }

    /** Back to the default order with everything shown (removes the override, no user content). */
    suspend fun resetLayout(context: Context) {
        val profiles = TemplarData.get(context).profiles
        profiles.resetSetting(profiles.activeProfileId.value, LAYOUT_SETTING_KEY)
    }
}
