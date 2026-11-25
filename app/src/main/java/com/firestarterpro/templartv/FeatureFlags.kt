package com.firestarterpro.templartv

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Runtime feature flags for Templar TV.
 *
 * Every flag has a built-in default and can be flipped from Settings → Features without a rebuild.
 * Experimental features default off. Passive foundations default on only when they preserve current
 * behavior. Values live in a small SharedPreferences file so they can be read synchronously from
 * services (for example the accessibility service) as well as observed as a [Flow] from the UI.
 */
enum class Flag(
    val key: String,
    val defaultEnabled: Boolean,
    val label: String,
    val description: String,
    val experimental: Boolean = false,
) {
    WATCH_HISTORY("watch_history", true, "Watch history", "Remember what you watched, locally, for Jump back in."),
    MEDIA_SESSION_OBSERVATION("media_session_observation", true, "Playback observation", "Read what is playing from apps' media sessions."),
    PROVIDER_ROWS("provider_rows", true, "Provider rows", "Show rows for your streaming providers."),
    PREVIEW_AND_UPCOMING("preview_and_upcoming", true, "New and upcoming", "Show new, upcoming and leaving-soon sections."),
    GOOGLE_HOME_HANDOFF("google_home_handoff", true, "Google Home shortcut", "Keep a way to open Google Home from Templar TV."),
    DIAGNOSTICS("diagnostics", true, "Diagnostics", "Keep local diagnostics about Templar TV itself."),
    PROVIDER_RECENT_SHELVES("provider_recent_shelves", true, "Recently played shelves", "Keep a recently played shelf for each provider."),
    SHELF_EDITING("shelf_editing", true, "Edit shelves", "Add, move, pin, hide and remove shelf tiles."),
    FAVOURITE_SHOWS("favourite_shows", true, "Favourite shows", "Keep an ordered list of favourite shows."),
    ROW_MANAGER("row_manager", true, "Row manager", "Reorder, hide and rename home rows."),
    BIG_BOX_HOME(
        "big_box_home", true, "Big-box home",
        "Use the new home (shelves, favourites, edit and move modes). Off brings back the classic For you screen.",
    ),
    HOME_VIEW_COLUMNS("home_view_columns", true, "Home view: Columns (Beta)", "Each shelf as a column of posters, side by side."),
    HOME_VIEW_RAIL_GRID("home_view_rail_grid", true, "Home view: Rail + grid (Beta)", "Shelf names on the left, the chosen shelf as a poster grid."),
    HOME_VIEW_SPOTLIGHT("home_view_spotlight", true, "Home view: Spotlight (Beta)", "Full-screen artwork for the focused show with one strip below."),
    HOME_VIEW_WALL("home_view_wall", true, "Home view: Wall (Beta)", "One big poster grid with filter chips across the top."),
    HOME_VIEW_LIST("home_view_list", true, "Home view: List (Beta)", "Text-first list of everything, grouped by shelf."),
    KEYBOARD_SHORTCUTS("keyboard_shortcuts", true, "Keyboard shortcuts", "Use your own key shortcuts inside Templar TV."),
    GLOBAL_SHORTCUTS(
        "global_shortcuts", false, "Shortcuts over other apps",
        "Let modifier-key and function-key shortcuts work while another app is open. Needs the accessibility service on.",
        experimental = true,
    ),
    JELLYFIN("jellyfin", false, "Jellyfin", "Show personal media from your Jellyfin server.", experimental = true),
    OCR_OBSERVATION("ocr_observation", false, "Screen reading (OCR)", "Opt-in screen text matching for watch history.", experimental = true),
    FOCUSED_TILE_OBSERVATION("focused_tile_observation", false, "Focused tile observation", "Use the focused tile as watch evidence.", experimental = true),
    CUSTOM_VOICE_ROUTING("custom_voice_routing", false, "Templar voice search", "Route media voice commands through Templar.", experimental = true),
    LOW_CONFIDENCE_REVIEW("low_confidence_review", false, "Review queue", "Send uncertain matches to a review row.", experimental = true),
    SETTINGS_KEY_CAPTURE(
        "settings_key_capture", false, "Capture Settings key",
        "Open Templar's quick menu from the remote's Settings key in any app. Off keeps Google's dashboard (and its Google Home panel) on that key. Needs the accessibility service on.",
        experimental = true,
    ),
    HOME_RETURN(
        "home_return", false, "Home opens Templar",
        "On Google TV Streamer the Home key can still open Google's launcher. When on, Templar brings itself back on top after a short Home press. Long-press Home (the Google Home dashboard) is left alone. Needs the accessibility service on and Templar set as Home app.",
        experimental = true,
    ),
    STAR_KEY_CAPTURE(
        "star_key_capture", false, "Capture star button (not working on Streamer)",
        "Tested on Google TV Streamer: Google treats the star button as a global key, so an accessibility service never receives it and this has no effect there. Kept for other devices.",
        experimental = true,
    ),
}

/** What the remote's star button does while [Flag.STAR_KEY_CAPTURE] is on. */
enum class StarAction(val key: String, val label: String) {
    QUICK_MENU("quick_menu", "Templar quick menu"),
    TEMPLAR_HOME("templar_home", "Templar home screen"),
    GOOGLE_DEFAULT("google_default", "Leave it to Google"),
}

object FeatureFlags {

    private const val PREFS = "templar_feature_flags"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, flag: Flag): Boolean =
        runCatching { prefs(context).getBoolean(flag.key, flag.defaultEnabled) }.getOrDefault(flag.defaultEnabled)

    fun set(context: Context, flag: Flag, enabled: Boolean) {
        prefs(context).edit().putBoolean(flag.key, enabled).apply()
    }

    fun starAction(context: Context): StarAction {
        val stored = runCatching { prefs(context).getString("star_action", null) }.getOrNull()
        return StarAction.entries.firstOrNull { it.key == stored } ?: StarAction.QUICK_MENU
    }

    fun setStarAction(context: Context, action: StarAction) {
        prefs(context).edit().putString("star_action", action.key).apply()
    }

    /** Back to the built-in default (removes the stored override). */
    fun reset(context: Context, flag: Flag) {
        prefs(context).edit().remove(flag.key).apply()
    }

    /** Emits the current state of every flag now, and again whenever any flag changes. */
    fun observe(context: Context): Flow<Map<Flag, Boolean>> = callbackFlow {
        val p = prefs(context)
        fun snapshot() = Flag.entries.associateWith { isEnabled(context, it) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        p.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot())
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Registers a callback for flag changes. Keep the returned listener and pass it to [unregister]. */
    fun register(context: Context, onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregister(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }
}
